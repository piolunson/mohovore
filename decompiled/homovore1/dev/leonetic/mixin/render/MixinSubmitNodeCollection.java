package dev.leonetic.mixin.render;

import dev.leonetic.util.render.SeeThroughRender;
import java.util.List;
import net.minecraft.class_1058;
import net.minecraft.class_11661;
import net.minecraft.class_11788;
import net.minecraft.class_1921;
import net.minecraft.class_3879;
import net.minecraft.class_4587;
import net.minecraft.class_630;
import net.minecraft.class_777;
import net.minecraft.class_811;
import net.minecraft.class_10444.class_10445;
import net.minecraft.class_11683.class_11792;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_11788.class)
public class MixinSubmitNodeCollection {
   @Shadow
   @Final
   private class_11661 field_62242;

   @Inject(method = "method_73490", at = @At("RETURN"))
   private <S> void homovore$cloneModelForSeeThrough(
      class_3879<? super S> model,
      S modelState,
      class_4587 poseStack,
      class_1921 renderType,
      int packedLight,
      int overlay,
      int blockPosition,
      class_1058 sprite,
      int flags,
      class_11792 crumbling,
      CallbackInfo ci
   ) {
      if (SeeThroughRender.active && !SeeThroughRender.cloning) {
         if (!SeeThroughRender.isGlintType(renderType)) {
            int sub = SeeThroughRender.cloneSubOrder++;
            SeeThroughRender.cloning = true;

            try {
               class_1921 seed = SeeThroughRender.wrapDepthSeed(renderType);
               this.field_62242
                  .method_73531(100000 + sub)
                  .method_73490(model, modelState, poseStack, seed, packedLight, overlay, blockPosition, sprite, flags, crumbling);
               class_1921 color = SeeThroughRender.wrapColor(renderType);
               this.field_62242
                  .method_73531(200000 + sub)
                  .method_73490(model, modelState, poseStack, color, packedLight, overlay, blockPosition, sprite, flags, crumbling);
            } finally {
               SeeThroughRender.cloning = false;
            }
         }
      }
   }

   @Inject(method = "method_73494", at = @At("RETURN"))
   private void homovore$cloneModelPartForSeeThrough(
      class_630 part,
      class_4587 poseStack,
      class_1921 renderType,
      int packedLight,
      int overlay,
      class_1058 sprite,
      boolean copyNormals,
      boolean fullBright,
      int flags,
      class_11792 crumbling,
      int data,
      CallbackInfo ci
   ) {
      if (SeeThroughRender.active && !SeeThroughRender.cloning) {
         if (!SeeThroughRender.isGlintType(renderType)) {
            int sub = SeeThroughRender.cloneSubOrder++;
            SeeThroughRender.cloning = true;

            try {
               class_1921 seed = SeeThroughRender.wrapDepthSeed(renderType);
               this.field_62242
                  .method_73531(100000 + sub)
                  .method_73494(part, poseStack, seed, packedLight, overlay, sprite, copyNormals, fullBright, flags, crumbling, data);
               class_1921 color = SeeThroughRender.wrapColor(renderType);
               this.field_62242
                  .method_73531(200000 + sub)
                  .method_73494(part, poseStack, color, packedLight, overlay, sprite, copyNormals, fullBright, flags, crumbling, data);
            } finally {
               SeeThroughRender.cloning = false;
            }
         }
      }
   }

   @Inject(method = "method_73480", at = @At("RETURN"))
   private void homovore$cloneItemForSeeThrough(
      class_4587 poseStack,
      class_811 context,
      int packedLight,
      int overlay,
      int data,
      int[] tints,
      List<class_777> quads,
      class_1921 renderType,
      class_10445 foil,
      CallbackInfo ci
   ) {
      if (SeeThroughRender.active && !SeeThroughRender.cloning) {
         if (!SeeThroughRender.isGlintType(renderType)) {
            int sub = SeeThroughRender.cloneSubOrder++;
            SeeThroughRender.cloning = true;

            try {
               class_1921 seed = SeeThroughRender.wrapDepthSeed(renderType);
               this.field_62242
                  .method_73531(100000 + sub)
                  .method_73480(poseStack, context, packedLight, overlay, data, tints, quads, seed, class_10445.field_55341);
               class_1921 color = SeeThroughRender.wrapColor(renderType);
               this.field_62242
                  .method_73531(200000 + sub)
                  .method_73480(poseStack, context, packedLight, overlay, data, tints, quads, color, class_10445.field_55341);
            } finally {
               SeeThroughRender.cloning = false;
            }
         }
      }
   }
}
