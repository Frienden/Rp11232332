
package com.frienden.resourcevision;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Map;

public final class VisionHud {
    private VisionHud(){}
    public static void render(DrawContext context,float tickDelta){
        MinecraftClient mc=MinecraftClient.getInstance();
        VisionConfig c=ResourceVisionClient.CONFIG;
        if(!c.hud||mc.player==null||mc.world==null||mc.options.hudHidden)return;
        ArrayList<String> lines=new ArrayList<>();
        lines.add("RESOURCE VISION  |  "+(ResourceVisionClient.SCANNER.busy()?"SCANNING":"READY"));
        lines.add("R: "+c.radius+"  Y: +/-"+c.vertical+"  Chunks: "+
                  ResourceVisionClient.SCANNER.scannedChunks()+"  Pending: "+ResourceVisionClient.SCANNER.pendingChunks());
        lines.add("ESP "+(c.esp?"ON":"OFF")+"  Fill "+(c.filled?"ON":"OFF")+"  X-Ray "+(c.xray?"ON":"OFF"));
        Map<Block,Integer> selected=c.resolved();
        int shown=0;
        for(Block block:selected.keySet()){
            if(shown++>=7){lines.add("... +"+(selected.size()-7)+" more");break;}
            int n=ResourceVisionClient.SCANNER.count(block);
            VisionScanner.Hit near=ResourceVisionClient.SCANNER.nearest(block);
            String name=Registries.BLOCK.getId(block).getPath();
            String info="["+n+"] "+name;
            if(near!=null){
                info+=" "+(int)Math.sqrt(near.distanceSq)+"m";
                if(c.showCoordinates){
                    BlockPos p=near.position();
                    info+="  "+p.getX()+", "+p.getY()+", "+p.getZ();
                }
            }
            lines.add(info);
        }
        if(selected.isEmpty())lines.add("RShift: menu  |  G: add aimed block");
        int width=0;
        for(String line:lines)width=Math.max(width,mc.textRenderer.getWidth(line));
        int left=6,top=6;
        context.fill(left,top,left+width+13,top+lines.size()*12+9,0xC9111722);
        context.fill(left,top,left+3,top+lines.size()*12+9,0xFF00D5FF);
        int y=top+6;
        for(int i=0;i<lines.size();i++){
            int color=i==0?0xFF00E5FF:i<3?0xFFD6E5EC:0xFFE7EBF0;
            context.drawTextWithShadow(mc.textRenderer,lines.get(i),left+8,y,color);
            y+=12;
        }
    }
}
