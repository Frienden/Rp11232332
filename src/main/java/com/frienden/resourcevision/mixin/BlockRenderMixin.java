
package com.frienden.resourcevision.mixin;

import com.frienden.resourcevision.ResourceVisionClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderManager.class)
public abstract class BlockRenderMixin {
    @Inject(method="renderBlock",at=@At("HEAD"),cancellable=true)
    private void resourcevision$hide(BlockState state,BlockPos pos,BlockRenderView world,
                                     MatrixStack matrices,VertexConsumer consumer,boolean cull,Random random,CallbackInfo ci){
        if(ResourceVisionClient.CONFIG!=null && ResourceVisionClient.CONFIG.xray &&
           !ResourceVisionClient.CONFIG.isSelected(state.getBlock()))ci.cancel();
    }
    @Inject(method="renderFluid",at=@At("HEAD"),cancellable=true)
    private void resourcevision$hideFluid(BlockPos pos,BlockRenderView world,
                                          VertexConsumer consumer,BlockState state,FluidState fluid,CallbackInfo ci){
        if(ResourceVisionClient.CONFIG!=null && ResourceVisionClient.CONFIG.xray &&
           !ResourceVisionClient.CONFIG.isSelected(state.getBlock()))ci.cancel();
    }
}
