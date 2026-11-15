
package com.frienden.resourcevision.mixin;

import com.frienden.resourcevision.ResourceVisionClient;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockOcclusionMixin {
    // In X-Ray, allow selected blocks to show all faces, even when sealed underground.
    @Inject(method="shouldDrawSide",at=@At("HEAD"),cancellable=true)
    private static void resourcevision$uncull(BlockState state,BlockView world,BlockPos pos,
                                               Direction side,BlockPos otherPos,CallbackInfoReturnable<Boolean> cir){
        if(ResourceVisionClient.CONFIG!=null && ResourceVisionClient.CONFIG.xray &&
           ResourceVisionClient.isSelected(state.getBlock()))cir.setReturnValue(true);
    }
}
