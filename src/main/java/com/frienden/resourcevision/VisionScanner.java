
package com.frienden.resourcevision;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import java.util.*;

public final class VisionScanner {
    public static final class Hit {
        public final long pos;
        public final Block block;
        public final double distanceSq;
        Hit(long pos,Block block,double distanceSq){this.pos=pos;this.block=block;this.distanceSq=distanceSq;}
        public BlockPos position(){return BlockPos.fromLong(pos);}
    }
    private static final class Cache {
        final int x,z;
        final Map<Block,ArrayList<Hit>> hits=new HashMap<>();
        final Map<Block,Integer> totals=new HashMap<>();
        long lastScan=0;
        boolean finished;
        Cache(int x,int z){this.x=x;this.z=z;}
        int storedHits=0;
    }
    private static final class Job {
        final int x,z,low,high;
        final Cache cache;
        int cursor=0;
        Job(int x,int z,int low,int high,Cache cache){
            this.x=x;this.z=z;this.low=low;this.high=high;this.cache=cache;
        }
        int volume(){return (high-low+1)*256;}
    }
    private final Map<Long,Cache> caches=new HashMap<>();
    private final Deque<Job> queue=new ArrayDeque<>();
    private final Set<Long> queued=new HashSet<>();
    private final ArrayList<Hit> visible=new ArrayList<>();
    private ClientWorld worldRef;
    private Map<Block,Integer> selected=Collections.emptyMap();
    private int trackedX=Integer.MIN_VALUE,trackedZ=Integer.MIN_VALUE,trackedY=Integer.MIN_VALUE;
    private int signature,scanRadius,scanVertical;
    private long ticks;
    private int scannedBlocks,verifyOffset;

    public void reset(){
        caches.clear();queue.clear();queued.clear();visible.clear();
        selected=Collections.emptyMap();trackedX=Integer.MIN_VALUE;
        signature=0;scannedBlocks=0;verifyOffset=0;
    }
    public void chunkChanged(int x,int z){removeChunk(x,z);trackedX=Integer.MIN_VALUE;}
    public void chunkUnloaded(int x,int z){removeChunk(x,z);trackedX=Integer.MIN_VALUE;}
    private void removeChunk(int x,int z){
        long k=ChunkPos.toLong(x,z);
        caches.remove(k);queued.remove(k);
        queue.removeIf(j->j.x==x&&j.z==z);
    }
    public boolean busy(){return !queue.isEmpty();}
    public int pendingChunks(){return queue.size();}
    public int scannedBlocks(){return scannedBlocks;}
    public int scannedChunks(){int n=0;for(Cache c:caches.values())if(c.finished)n++;return n;}
    public List<Hit> results(){return Collections.unmodifiableList(visible);}
    public int count(Block b){
        int n=0;for(Cache c:caches.values())n+=c.totals.getOrDefault(b,0);
        return n;
    }
    public Hit nearest(Block b){
        for(Hit h:visible)if(h.block==b)return h;
        return null;
    }
    public void tick(MinecraftClient mc){
        if(mc.world==null || mc.player==null){
            if(worldRef!=null)reset();
            worldRef=null;return;
        }
        ClientWorld world=mc.world;
        if(worldRef!=world){reset();worldRef=world;}
        ticks++;
        VisionConfig cfg=ResourceVisionClient.CONFIG;
        if(cfg.targets.isEmpty()){
            if(!caches.isEmpty()||!queue.isEmpty())reset();
            return;
        }
        int cx=mc.player.getBlockX()>>4,cz=mc.player.getBlockZ()>>4,y=mc.player.getBlockY();
        int sig=cfg.targets.keySet().hashCode();
        if(sig!=signature || cx!=trackedX || cz!=trackedZ || Math.abs(y-trackedY)>12 ||
                scanRadius!=cfg.radius || scanVertical!=cfg.vertical){
            if(sig!=signature||scanRadius!=cfg.radius||scanVertical!=cfg.vertical || Math.abs(y-trackedY)>12)reset();
            selected=cfg.resolved();
            signature=sig;trackedX=cx;trackedZ=cz;trackedY=y;
            scanRadius=cfg.radius;scanVertical=cfg.vertical;
            schedule(world,cx,cz,y);
        }
        // One old chunk is rescanned periodically to detect changes not in the small sampled cache.
        if(ticks%160==0 && queue.size()<4){
            Cache oldest=null;
            for(Cache c:caches.values())if(c.finished && (oldest==null||c.lastScan<oldest.lastScan))oldest=c;
            if(oldest!=null)enqueue(world,oldest.x,oldest.z,y,true);
        }
        scan(world,mc.player.getPos(),cfg.scanBudget);
        if(ticks%5==0)validate(world,80);
        if(ticks%6==0 || (!busy() && visible.isEmpty()))refreshVisible(mc);
    }
    private void schedule(ClientWorld world,int cx,int cz,int y){
        int bound=(scanRadius+15)/16+1;
        caches.entrySet().removeIf(e->
                Math.abs(e.getValue().x-cx)>bound || Math.abs(e.getValue().z-cz)>bound);
        for(int dx=-bound;dx<=bound;dx++)for(int dz=-bound;dz<=bound;dz++){
            int x=cx+dx,z=cz+dz;
            int px=cx*16+8,pz=cz*16+8;
            int nearestX=Math.max(x*16,Math.min(px,x*16+15));
            int nearestZ=Math.max(z*16,Math.min(pz,z*16+15));
            long ax=nearestX-px,az=nearestZ-pz;
            if(ax*ax+az*az>(long)(scanRadius+16)*(scanRadius+16))continue;
            enqueue(world,x,z,y,false);
        }
    }
    private void enqueue(ClientWorld world,int cx,int cz,int y,boolean replace){
        long k=ChunkPos.toLong(cx,cz);
        if(queued.contains(k)||(!replace&&caches.containsKey(k)))return;
        WorldChunk chunk=world.getChunkManager().getChunk(cx,cz,ChunkStatus.FULL,false);
        if(chunk==null)return;
        int low=Math.max(world.getBottomY(),y-scanVertical),high=Math.min(world.getTopY()-1,y+scanVertical);
        if(low>high)return;
        Cache cache=new Cache(cx,cz);
        caches.put(k,cache);queued.add(k);
        queue.addLast(new Job(cx,cz,low,high,cache));
    }
    private void scan(ClientWorld world,Vec3d player,int budget){
        BlockPos.Mutable blockPos=new BlockPos.Mutable();
        int r2=scanRadius*scanRadius;
        while(budget>0&&!queue.isEmpty()){
            Job job=queue.peekFirst();
            WorldChunk chunk=world.getChunkManager().getChunk(job.x,job.z,ChunkStatus.FULL,false);
            if(chunk==null){
                queue.removeFirst();queued.remove(ChunkPos.toLong(job.x,job.z));
                caches.remove(ChunkPos.toLong(job.x,job.z));continue;
            }
            int h=job.high-job.low+1;
            int work=Math.min(budget,job.volume()-job.cursor);
            for(int step=0;step<work;step++){
                int index=job.cursor++;
                int lx=(index/h)&15,lz=(index/h)>>4;
                int x=(job.x<<4)+lx,z=(job.z<<4)+lz,y=job.low+index%h;
                double a=x+.5-player.x,b=z+.5-player.z;
                if(a*a+b*b>r2)continue;
                blockPos.set(x,y,z);
                Block block=chunk.getBlockState(blockPos).getBlock();
                if(!selected.containsKey(block))continue;
                int count=job.cache.totals.merge(block,1,Integer::sum);
                ArrayList<Hit> hits=job.cache.hits.computeIfAbsent(block,k->new ArrayList<>());
                Hit found=new Hit(blockPos.asLong(),block,0);
                if(hits.size()<256 && job.cache.storedHits<1024){hits.add(found);job.cache.storedHits++;}
                else if(!hits.isEmpty()){
                    // Reservoir sampling avoids retaining only the bottom of common-block veins.
                    int pick=(int)((blockPos.asLong()^(blockPos.asLong()>>>19))&0x7fffffffL)%count;
                    if(pick<hits.size())hits.set(pick,found);
                }
            }
            budget-=work;scannedBlocks+=work;
            if(job.cursor>=job.volume()){
                job.cache.finished=true;job.cache.lastScan=ticks;
                queue.removeFirst();queued.remove(ChunkPos.toLong(job.x,job.z));
            }
        }
    }
    private void validate(ClientWorld world,int budget){
        if(caches.isEmpty())return;
        ArrayList<Cache> all=new ArrayList<>(caches.values());
        int pos=verifyOffset%all.size(),looked=0;
        while(budget>0 && looked<all.size()){
            Cache cache=all.get(pos);
            for(var entry:cache.hits.entrySet()){
                ArrayList<Hit> list=entry.getValue();
                for(int i=0;i<list.size() && budget>0;i++){
                    Hit hit=list.get(i);
                    if(world.getBlockState(hit.position()).getBlock()!=hit.block){
                        list.remove(i--);cache.storedHits=Math.max(0,cache.storedHits-1);
                        cache.totals.computeIfPresent(hit.block,(key,v)->Math.max(0,v-1));
                    }
                    budget--;
                    // Only one small sample per cached type, not a full world scan.
                    if(i>=4)break;
                }
                if(budget<=0)break;
            }
            looked++;pos=(pos+1)%all.size();
        }
        verifyOffset=pos;
    }

    private void refreshVisible(MinecraftClient mc){
        if(mc.player==null)return;
        Vec3d p=mc.player.getPos();
        int max=ResourceVisionClient.CONFIG.maxRender;
        // Reserve a fair share of the render budget for every selected block.
        // Common stone can no longer crowd all diamond or ancient-debris hits off screen.
        int quota=Math.max(1,max/Math.max(1,selected.size()));
        Map<Block,PriorityQueue<Hit>> perType=new HashMap<>();
        double maxSq=(double)scanRadius*scanRadius;
        for(Cache c:caches.values())for(var list:c.hits.values())for(Hit h:list){
            BlockPos pos=h.position();
            double d=pos.getSquaredDistance(p.x,p.y,p.z);
            if(d>maxSq||!selected.containsKey(h.block))continue;
            PriorityQueue<Hit> heap=perType.computeIfAbsent(h.block,k->
                new PriorityQueue<>(Comparator.comparingDouble(a->-a.distanceSq)));
            if(heap.size()<quota)heap.add(new Hit(h.pos,h.block,d));
            else if(d<heap.peek().distanceSq){heap.poll();heap.add(new Hit(h.pos,h.block,d));}
        }
        visible.clear();
        for(var list:perType.values())visible.addAll(list);
        visible.sort(Comparator.comparingDouble(h->h.distanceSq));
        if(visible.size()>max)visible.subList(max,visible.size()).clear();
    }
}
