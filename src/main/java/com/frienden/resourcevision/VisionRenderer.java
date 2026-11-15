
package com.frienden.resourcevision;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import java.util.*;

public final class VisionRenderer {
    private VisionRenderer(){}
    private static final class Marker {
        int x1,y1,z1,x2,y2,z2,color;
        Marker(BlockPos pos,int color){
            x1=pos.getX();y1=pos.getY();z1=pos.getZ();
            x2=x1+1;y2=y1+1;z2=z1+1;this.color=color;
        }
        void absorb(BlockPos p){
            x1=Math.min(x1,p.getX());y1=Math.min(y1,p.getY());z1=Math.min(z1,p.getZ());
            x2=Math.max(x2,p.getX()+1);y2=Math.max(y2,p.getY()+1);z2=Math.max(z2,p.getZ()+1);
        }
    }
    public static void render(WorldRenderContext ctx){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null)return;
        VisionConfig cfg=ResourceVisionClient.CONFIG;
        if((!cfg.esp&&!cfg.filled&&!cfg.tracers)||cfg.targets.isEmpty())return;
        List<VisionScanner.Hit> hits=ResourceVisionClient.SCANNER.results();
        if(hits.isEmpty()||ctx.matrixStack()==null)return;
        ArrayList<VisionScanner.Hit> valid=new ArrayList<>(hits.size());
        for(VisionScanner.Hit h:hits)
            if(mc.world.getBlockState(h.position()).getBlock()==h.block)valid.add(h);
        if(valid.isEmpty())return;
        MatrixStack matrices=ctx.matrixStack();
        Vec3d camera=ctx.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x,-camera.y,-camera.z);
        Matrix4f mat=matrices.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        try {
            List<Marker> markers=cfg.grouping?group(valid,cfg):single(valid,cfg);
            if(cfg.filled) {
                BufferBuilder b=Tessellator.getInstance().getBuffer();
                b.begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
                for(Marker v:markers)filled(b,mat,v,cfg.opacity*255/100);
                BufferRenderer.drawWithGlobalProgram(b.end());
            }
            if(cfg.esp || cfg.tracers) {
                BufferBuilder b=Tessellator.getInstance().getBuffer();
                b.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
                if(cfg.esp)for(Marker v:markers)outline(b,mat,v);
                if(cfg.tracers) {
                    // Tracer targets: nearest N, never the whole set.
                    int limit=Math.min(cfg.tracerLimit,valid.size());
                    for(int i=0;i<limit;i++){
                        VisionScanner.Hit h=valid.get(i);
                        BlockPos p=h.position();
                        int color=cfg.color(h.block);
                        line(b,mat,camera.x,camera.y,camera.z,
                             p.getX()+.5,p.getY()+.5,p.getZ()+.5,color,175);
                    }
                }
                BufferRenderer.drawWithGlobalProgram(b.end());
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            matrices.pop();
        }
    }
    private static List<Marker> single(List<VisionScanner.Hit> hits,VisionConfig cfg){
        ArrayList<Marker> result=new ArrayList<>(hits.size());
        for(var h:hits)result.add(new Marker(h.position(),cfg.color(h.block)));
        return result;
    }
    private static List<Marker> group(List<VisionScanner.Hit> hits,VisionConfig cfg){
        // Six-neighbor clusters, bounded by maxRender (no global-world flood fill).
        Map<Long,VisionScanner.Hit> points=new HashMap<>();
        for(var h:hits)points.put(h.pos,h);
        Set<Long> used=new HashSet<>();
        ArrayList<Marker> out=new ArrayList<>();
        for(var first:hits){
            if(!used.add(first.pos))continue;
            Marker marker=new Marker(first.position(),cfg.color(first.block));
            ArrayDeque<VisionScanner.Hit> todo=new ArrayDeque<>();
            todo.add(first);
            while(!todo.isEmpty()){
                var h=todo.removeFirst();
                BlockPos p=h.position();
                marker.absorb(p);
                long[] near={
                    BlockPos.asLong(p.getX()+1,p.getY(),p.getZ()),
                    BlockPos.asLong(p.getX()-1,p.getY(),p.getZ()),
                    BlockPos.asLong(p.getX(),p.getY()+1,p.getZ()),
                    BlockPos.asLong(p.getX(),p.getY()-1,p.getZ()),
                    BlockPos.asLong(p.getX(),p.getY(),p.getZ()+1),
                    BlockPos.asLong(p.getX(),p.getY(),p.getZ()-1)
                };
                for(long k:near){
                    var other=points.get(k);
                    if(other!=null&&other.block==first.block&&used.add(k))todo.addLast(other);
                }
            }
            out.add(marker);
        }
        return out;
    }
    private static void outline(BufferBuilder b,Matrix4f m,Marker v){
        int c=v.color;
        double x=v.x1,y=v.y1,z=v.z1,X=v.x2,Y=v.y2,Z=v.z2;
        line(b,m,x,y,z,X,y,z,c,255);line(b,m,X,y,z,X,y,Z,c,255);
        line(b,m,X,y,Z,x,y,Z,c,255);line(b,m,x,y,Z,x,y,z,c,255);
        line(b,m,x,Y,z,X,Y,z,c,255);line(b,m,X,Y,z,X,Y,Z,c,255);
        line(b,m,X,Y,Z,x,Y,Z,c,255);line(b,m,x,Y,Z,x,Y,z,c,255);
        line(b,m,x,y,z,x,Y,z,c,255);line(b,m,X,y,z,X,Y,z,c,255);
        line(b,m,x,y,Z,x,Y,Z,c,255);line(b,m,X,y,Z,X,Y,Z,c,255);
    }
    private static void line(BufferBuilder b,Matrix4f m,double x,double y,double z,double X,double Y,double Z,int color,int alpha){
        vertex(b,m,x,y,z,color,alpha);
        vertex(b,m,X,Y,Z,color,alpha);
    }
    private static void filled(BufferBuilder b,Matrix4f m,Marker v,int opacity){
        double x=v.x1,y=v.y1,z=v.z1,X=v.x2,Y=v.y2,Z=v.z2;
        int c=v.color;
        quad(b,m,x,y,z,X,y,z,X,Y,z,x,Y,z,c,opacity);
        quad(b,m,x,y,Z,x,Y,Z,X,Y,Z,X,y,Z,c,opacity);
        quad(b,m,x,y,z,x,Y,z,x,Y,Z,x,y,Z,c,opacity);
        quad(b,m,X,y,z,X,y,Z,X,Y,Z,X,Y,z,c,opacity);
        quad(b,m,x,Y,z,X,Y,z,X,Y,Z,x,Y,Z,c,opacity);
        quad(b,m,x,y,z,x,y,Z,X,y,Z,X,y,z,c,opacity);
    }
    private static void quad(BufferBuilder b,Matrix4f m,
                             double x1,double y1,double z1,double x2,double y2,double z2,
                             double x3,double y3,double z3,double x4,double y4,double z4,int color,int a){
        vertex(b,m,x1,y1,z1,color,a);vertex(b,m,x2,y2,z2,color,a);
        vertex(b,m,x3,y3,z3,color,a);vertex(b,m,x4,y4,z4,color,a);
    }
    private static void vertex(BufferBuilder b,Matrix4f m,double x,double y,double z,int color,int a){
        b.vertex(m,(float)x,(float)y,(float)z).color((color>>16)&255,(color>>8)&255,color&255,a).next();
    }
}
