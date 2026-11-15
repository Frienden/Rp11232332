
package com.frienden.resourcevision;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VisionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("resourcevision.json");
    public LinkedHashMap<String,Integer> targets=new LinkedHashMap<>();
    public boolean esp=true, filled=false, tracers=false, xray=false, hud=true, grouping=false, showCoordinates=true;
    public int radius=48, vertical=48, opacity=35, maxRender=700, scanBudget=6000, tracerLimit=16;
    public static final int[] PALETTE={0x00E5FF,0xFFCC33,0xFF507F,0x99E265,0xBB88FF,0xFF914D,0x58A6FF,0xFFFFFF,0xF2C2FF};

    public static VisionConfig load() {
        try {
            if(Files.exists(FILE)) {
                try(Reader reader=Files.newBufferedReader(FILE)) {
                    VisionConfig config=GSON.fromJson(reader,VisionConfig.class);
                    if(config!=null){config.normalize();return config;}
                }
            }
        } catch(Exception e){ResourceVisionClient.LOG.warn("Invalid Resource Vision config",e);}
        return new VisionConfig();
    }
    private void normalize(){
        if(targets==null) targets=new LinkedHashMap<>();
        targets.entrySet().removeIf(e->e.getKey()==null || Identifier.tryParse(e.getKey())==null || e.getValue()==null);
        radius=Math.max(8,Math.min(128,radius));
        vertical=Math.max(8,Math.min(192,vertical));
        opacity=Math.max(5,Math.min(90,opacity));
        maxRender=Math.max(50,Math.min(2500,maxRender));
        scanBudget=Math.max(500,Math.min(16000,scanBudget));
        tracerLimit=Math.max(1,Math.min(48,tracerLimit));
    }
    public void save(){
        normalize();
        try{
            Files.createDirectories(FILE.getParent());
            try(Writer w=Files.newBufferedWriter(FILE)){GSON.toJson(this,w);}
        }catch(Exception e){ResourceVisionClient.LOG.warn("Could not save config",e);}
    }
    public boolean isSelected(Block b){return targets.containsKey(Registries.BLOCK.getId(b).toString());}
    public int color(Block b){return targets.getOrDefault(Registries.BLOCK.getId(b).toString(),0xFFFFFF);}
    public boolean add(Block b){
        String id=Registries.BLOCK.getId(b).toString();
        if(targets.containsKey(id))return false;
        targets.put(id,PALETTE[targets.size()%PALETTE.length]);save();return true;
    }
    public void remove(String id){targets.remove(id);save();}
    public void cycleColor(String id){
        Integer value=targets.get(id);
        if(value==null)return;
        int i=0;
        for(;i<PALETTE.length;i++) if(PALETTE[i]==value)break;
        targets.put(id,PALETTE[(i+1)%PALETTE.length]);save();
    }
    public Map<Block,Integer> resolved(){
        Map<Block,Integer> out=new LinkedHashMap<>();
        for(var entry:targets.entrySet()){
            Identifier id=Identifier.tryParse(entry.getKey());
            if(id!=null && Registries.BLOCK.containsId(id))out.put(Registries.BLOCK.get(id),entry.getValue());
        }
        return out;
    }
}
