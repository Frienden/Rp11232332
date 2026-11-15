
package com.frienden.resourcevision;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.*;

public final class VisionScreen extends Screen {
    private static final int PANEL=0xF2101722,BOX=0xFF172334,DARK=0xFF0C1420;
    private static final int TEXT=0xFFE6EEF6,FADED=0xFF8AA1B8,ACCENT=0xFF00D9FF;
    private static final int ROW=19;
    private final ArrayList<Block> matching=new ArrayList<>();
    private TextFieldWidget search;
    private int left,top,w,h,colA,colB,colC,scrollAvailable,scrollSelected;
    private String category="ALL";
    private boolean confirmReset=false;

    public VisionScreen(){super(Text.literal("Resource Vision"));}
    @Override protected void init(){
        w=Math.min(850,width-22);h=Math.min(490,height-22);
        left=(width-w)/2;top=(height-h)/2;
        colA=(w-36)*36/100;colB=(w-36)*29/100;colC=w-36-colA-colB;
        search=new TextFieldWidget(textRenderer,left+14,top+69,colA-22,20,Text.literal("Search blocks"));
        search.setMaxLength(128);
        search.setPlaceholder(Text.literal("Name or minecraft:block_id"));
        search.setChangedListener(s->{scrollAvailable=0;filter();});
        addDrawableChild(search);
        filter();
    }
    private void filter(){
        matching.clear();
        String s=search==null?"":search.getText().toLowerCase(Locale.ROOT).trim();
        for(Block block:Registries.BLOCK){
            Identifier id=Registries.BLOCK.getId(block);
            String path=id.getPath();
            if(!s.isEmpty()&&!id.toString().contains(s)&&!block.getName().getString().toLowerCase(Locale.ROOT).contains(s))continue;
            if(!category.equals("ALL")&&!categorizes(path,category))continue;
            matching.add(block);
        }
        matching.sort(Comparator.comparing(b->Registries.BLOCK.getId(b).toString()));
    }
    private static boolean categorizes(String id,String which){
        boolean ore=id.contains("_ore")||id.contains("ancient_debris")||id.equals("nether_quartz_ore");
        boolean stone=id.contains("stone")||id.contains("diorite")||id.contains("andesite")||id.contains("granite")||id.contains("tuff")||id.contains("basalt")||id.contains("cobblestone")||id.contains("deepslate");
        boolean wood=id.contains("_log")||id.contains("_wood")||id.contains("_planks")||id.contains("_stem")||id.contains("bamboo");
        return switch(which){
            case "ORES"->ore;
            case "STONE"->stone;
            case "WOOD"->wood;
            case "OTHER"->!ore&&!stone&&!wood;
            default->true;
        };
    }
    private int sectionTop(){return top+124;}
    private int bottom(){return top+h-40;}
    private int rows(){return Math.max(1,(bottom()-sectionTop())/ROW);}
    private int bx(){return left+12+colA+9;}
    private int cx(){return bx()+colB+9;}
    private boolean inside(double x,double y,int a,int b,int ww,int hh){
        return x>=a&&x<a+ww&&y>=b&&y<b+hh;
    }
    private static int rgba(int color){return 0xFF000000|color;}
    @Override public void render(DrawContext d,int mx,int my,float dt){
        d.fill(0,0,width,height,0xCC050910);
        d.fill(left-2,top-2,left+w+2,top+h+2,0xFF2A4257);
        d.fill(left,top,left+w,top+h,PANEL);
        d.fill(left,top,left+w,top+4,ACCENT);
        d.drawTextWithShadow(textRenderer,"RESOURCE VISION",left+14,top+15,ACCENT);
        d.drawTextWithShadow(textRenderer,"FABRIC 1.20.1    |    CLIENT ONLY",left+167,top+15,FADED);
        d.drawTextWithShadow(textRenderer,"RShift: menu  •  G: pick block  •  H: HUD",left+14,top+35,TEXT);
        d.drawTextWithShadow(textRenderer,"AVAILABLE BLOCKS  ("+matching.size()+")",left+14,top+55,TEXT);
        // Sections and category filters.
        d.fill(left+9,top+101,left+colA+9,bottom()+1,DARK);
        d.fill(bx()-3,top+55,bx()+colB+3,bottom()+1,DARK);
        d.fill(cx()-3,top+55,left+w-9,bottom()+1,DARK);
        String[] cats={"ALL","ORES","STONE","WOOD","OTHER"};
        int tabW=(colA-14)/5;
        for(int i=0;i<cats.length;i++){
            int tx=left+14+i*tabW;
            d.fill(tx,top+99,tx+tabW-2,top+119,category.equals(cats[i])?0xFF175578:BOX);
            d.drawCenteredTextWithShadow(textRenderer,cats[i],tx+(tabW-2)/2,top+105,category.equals(cats[i])?ACCENT:FADED);
        }
        // Available list.
        int i0=Math.min(scrollAvailable,Math.max(0,matching.size()-rows()));
        for(int i=i0;i<matching.size()&&i<i0+rows();i++){
            Block b=matching.get(i);
            String id=Registries.BLOCK.getId(b).toString();
            int y=sectionTop()+(i-i0)*ROW;
            if(y+ROW>bottom()+1)break;
            boolean marked=ResourceVisionClient.CONFIG.targets.containsKey(id);
            d.fill(left+12,y,left+colA+7,y+ROW-1,marked?0xFF124F55:(i%2==0?0xFF101C2B:0xFF142134));
            String name=b.getName().getString();
            d.drawTextWithShadow(textRenderer,shortText(name,colA-40),left+17,y+5,marked?ACCENT:TEXT);
            d.drawTextWithShadow(textRenderer,marked?"✓":"+",left+colA-13,y+5,marked?0xFF8EFFB0:FADED);
        }
        // Selected list.
        d.drawTextWithShadow(textRenderer,"TRACKED  ("+ResourceVisionClient.CONFIG.targets.size()+")",bx()+5,top+66,ACCENT);
        d.drawTextWithShadow(textRenderer,"Click: remove    •    square: color",bx()+5,top+87,FADED);
        List<String> picked=new ArrayList<>(ResourceVisionClient.CONFIG.targets.keySet());
        int s0=Math.min(scrollSelected,Math.max(0,picked.size()-rows()));
        for(int i=s0;i<picked.size()&&i<s0+rows();i++){
            String id=picked.get(i);
            int y=sectionTop()+(i-s0)*ROW;
            d.fill(bx()+1,y,bx()+colB-2,y+ROW-1,(i%2==0?0xFF17283A:BOX));
            d.fill(bx()+6,y+4,bx()+17,y+15,rgba(ResourceVisionClient.CONFIG.targets.get(id)));
            d.drawTextWithShadow(textRenderer,shortText(id,colB-56),bx()+23,y+5,TEXT);
            d.drawTextWithShadow(textRenderer,"×",bx()+colB-16,y+5,0xFFFF7F98);
        }
        if(picked.isEmpty()) {
            d.drawTextWithShadow(textRenderer,"Select blocks on the left",bx()+7,sectionTop()+5,FADED);
            d.drawTextWithShadow(textRenderer,"or aim + press G",bx()+7,sectionTop()+20,FADED);
        }
        // Settings.
        int x=cx()+3;
        d.drawTextWithShadow(textRenderer,"RENDERING  /  SCANNER",x,top+65,ACCENT);
        d.drawTextWithShadow(textRenderer,"Independent display modes",x,top+83,FADED);
        int y=top+105;
        toggle(d,x,y,"OUTLINE ESP",ResourceVisionClient.CONFIG.esp);y+=24;
        toggle(d,x,y,"FILLED ESP",ResourceVisionClient.CONFIG.filled);y+=24;
        toggle(d,x,y,"X-RAY (HIDE)",ResourceVisionClient.CONFIG.xray);y+=24;
        toggle(d,x,y,"TRACERS",ResourceVisionClient.CONFIG.tracers);y+=24;
        toggle(d,x,y,"HUD",ResourceVisionClient.CONFIG.hud);y+=24;
        toggle(d,x,y,"MERGE VEINS",ResourceVisionClient.CONFIG.grouping);y+=31;
        option(d,x,y,"Scan radius",ResourceVisionClient.CONFIG.radius,"blocks");y+=24;
        option(d,x,y,"Vertical +/-",ResourceVisionClient.CONFIG.vertical,"blocks");y+=24;
        option(d,x,y,"Fill alpha",ResourceVisionClient.CONFIG.opacity,"%");y+=24;
        option(d,x,y,"Render cap",ResourceVisionClient.CONFIG.maxRender,"");y+=24;
        option(d,x,y,"Scan budget",ResourceVisionClient.CONFIG.scanBudget,"/tick");y+=30;
        if(y+19<top+h-18){
            d.fill(x,y,x+colC-12,y+20,confirmReset?0xFF762C38:0xFF2C4156);
            d.drawCenteredTextWithShadow(textRenderer,confirmReset?"CONFIRM RESET":"RESET SETTINGS",
                                         x+(colC-12)/2,y+6,TEXT);
        }
        d.drawTextWithShadow(textRenderer,"Server supplies the block data.",left+14,top+h-26,FADED);
        d.drawTextWithShadow(textRenderer,"ESC: close  |  Keys: Minecraft controls",left+w-260,top+h-26,FADED);
        super.render(d,mx,my,dt);
    }
    private String shortText(String text,int max){
        if(textRenderer.getWidth(text)<=max)return text;
        while(text.length()>3 && textRenderer.getWidth(text+"...")>max)text=text.substring(0,text.length()-1);
        return text+"...";
    }
    private void toggle(DrawContext d,int x,int y,String label,boolean enabled){
        d.drawTextWithShadow(textRenderer,label,x+1,y+6,TEXT);
        int right=cx()+colC-11;
        d.fill(right-47,y+2,right,y+20,enabled?0xFF13795C:0xFF3A4054);
        d.drawCenteredTextWithShadow(textRenderer,enabled?"ON":"OFF",right-23,y+7,0xFFFFFFFF);
    }
    private void option(DrawContext d,int x,int y,String label,int val,String suffix){
        d.drawTextWithShadow(textRenderer,label,x+1,y+7,TEXT);
        int right=cx()+colC-11;
        d.fill(right-110,y+2,right-89,y+21,BOX);d.fill(right-21,y+2,right,y+21,BOX);
        d.drawCenteredTextWithShadow(textRenderer,"-",right-99,y+7,ACCENT);
        d.drawCenteredTextWithShadow(textRenderer,"+",right-10,y+7,ACCENT);
        d.drawCenteredTextWithShadow(textRenderer,val+suffix,right-54,y+7,TEXT);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        if(inside(mx,my,left+14,top+99,colA-14,20)){
            String[] cats={"ALL","ORES","STONE","WOOD","OTHER"};
            int tabW=(colA-14)/5;
            int index=(int)(mx-left-14)/tabW;
            if(index>=0&&index<5){category=cats[index];scrollAvailable=0;filter();return true;}
        }
        if(inside(mx,my,left+12,sectionTop(),colA-5,bottom()-sectionTop())){
            int i=scrollAvailable+((int)my-sectionTop())/ROW;
            if(i>=0&&i<matching.size()){
                Block block=matching.get(i);
                String id=Registries.BLOCK.getId(block).toString();
                if(ResourceVisionClient.CONFIG.targets.containsKey(id))ResourceVisionClient.CONFIG.remove(id);
                else ResourceVisionClient.CONFIG.add(block);
                ResourceVisionClient.changed(true);
                return true;
            }
        }
        if(inside(mx,my,bx(),sectionTop(),colB,bottom()-sectionTop())){
            int i=scrollSelected+((int)my-sectionTop())/ROW;
            List<String> picked=new ArrayList<>(ResourceVisionClient.CONFIG.targets.keySet());
            if(i>=0&&i<picked.size()){
                String id=picked.get(i);
                if(mx<bx()+22)ResourceVisionClient.CONFIG.cycleColor(id);
                else ResourceVisionClient.CONFIG.remove(id);
                ResourceVisionClient.changed(true);return true;
            }
        }
        int x=cx()+3;
        int toggleStart=top+105;
        if(inside(mx,my,x,toggleStart,colC-14,6*24)){
            int row=((int)my-toggleStart)/24;
            VisionConfig c=ResourceVisionClient.CONFIG;
            switch(row){
                case 0->c.esp=!c.esp;
                case 1->c.filled=!c.filled;
                case 2->{c.xray=!c.xray;ResourceVisionClient.reloadTerrain();}
                case 3->c.tracers=!c.tracers;
                case 4->c.hud=!c.hud;
                case 5->c.grouping=!c.grouping;
            }
            c.save();return true;
        }
        int optionStart=top+105+6*24+7;
        if(inside(mx,my,x,optionStart,colC-14,5*24)){
            int row=((int)my-optionStart)/24;
            int right=cx()+colC-11;
            int step=mx>=right-28?1:mx<=right-85?-1:0;
            if(step!=0){
                VisionConfig c=ResourceVisionClient.CONFIG;
                switch(row){
                    case 0->{c.radius=Math.max(8,Math.min(128,c.radius+8*step));ResourceVisionClient.changed(false);}
                    case 1->{c.vertical=Math.max(8,Math.min(192,c.vertical+8*step));ResourceVisionClient.changed(false);}
                    case 2->c.opacity=Math.max(5,Math.min(90,c.opacity+5*step));
                    case 3->c.maxRender=Math.max(50,Math.min(2500,c.maxRender+100*step));
                    case 4->c.scanBudget=Math.max(500,Math.min(16000,c.scanBudget+1000*step));
                }
                c.save();
            }
            return true;
        }
        int resetY=optionStart+5*24+6;
        if(inside(mx,my,x,resetY,colC-12,20)){
            if(confirmReset){
                ResourceVisionClient.CONFIG=new VisionConfig();
                ResourceVisionClient.changed(true);
                ResourceVisionClient.reloadTerrain();
                confirmReset=false;scrollSelected=0;
            }else confirmReset=true;
            return true;
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double amount){
        if(inside(mx,my,left+12,sectionTop(),colA-5,bottom()-sectionTop())){
            scrollAvailable=Math.max(0,Math.min(Math.max(0,matching.size()-rows()),scrollAvailable-(int)Math.signum(amount)*3));
            return true;
        }
        if(inside(mx,my,bx(),sectionTop(),colB,bottom()-sectionTop())){
            scrollSelected=Math.max(0,Math.min(Math.max(0,ResourceVisionClient.CONFIG.targets.size()-rows()),scrollSelected-(int)Math.signum(amount)*3));
            return true;
        }
        return super.mouseScrolled(mx,my,amount);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){
        ResourceVisionClient.CONFIG.save();
        if(client!=null)client.setScreen(null);
    }
}
