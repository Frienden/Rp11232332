
package com.frienden.resourcevision;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceVisionClient implements ClientModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("ResourceVision");
    public static VisionConfig CONFIG;
    public static final VisionScanner SCANNER=new VisionScanner();
    private static KeyBinding menuKey,pickKey,hudKey;
    @Override public void onInitializeClient(){
        CONFIG=VisionConfig.load();
        menuKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.resourcevision.menu",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_RIGHT_SHIFT,"category.resourcevision"));
        pickKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.resourcevision.pick",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_G,"category.resourcevision"));
        hudKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.resourcevision.hud",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_H,"category.resourcevision"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(menuKey.wasPressed()) client.setScreen(new VisionScreen());
            while(pickKey.wasPressed()) {
                if(client.world==null || client.player==null || client.crosshairTarget==null || client.crosshairTarget.getType()!=HitResult.Type.BLOCK)continue;
                BlockHitResult hit=(BlockHitResult)client.crosshairTarget;
                Block b=client.world.getBlockState(hit.getBlockPos()).getBlock();
                if(CONFIG.add(b)){
                    changed(true);
                    client.player.sendMessage(Text.literal("Resource Vision: + "+Registries.BLOCK.getId(b)),true);
                }
            }
            while(hudKey.wasPressed()){CONFIG.hud=!CONFIG.hud;CONFIG.save();}
            SCANNER.tick(client);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(VisionRenderer::render);
        HudRenderCallback.EVENT.register(VisionHud::render);
        ClientChunkEvents.CHUNK_LOAD.register((world,chunk)->SCANNER.chunkChanged(chunk.getPos().x,chunk.getPos().z));
        ClientChunkEvents.CHUNK_UNLOAD.register((world,chunk)->SCANNER.chunkUnloaded(chunk.getPos().x,chunk.getPos().z));
        LOG.info("Resource Vision initialized");
    }
    public static void changed(boolean terrain){
        CONFIG.save();SCANNER.reset();
        if(terrain&&CONFIG.xray)reloadTerrain();
    }
    public static void reloadTerrain(){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.world!=null)client.worldRenderer.reload();
    }
}
