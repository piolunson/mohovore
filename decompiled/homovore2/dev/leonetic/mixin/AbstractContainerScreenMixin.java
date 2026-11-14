package dev.leonetic.mixin;

import dev.leonetic.feature.module.impl.FixesModule;
import dev.leonetic.feature.module.impl.ShulkerPreviewModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_11909;
import net.minecraft.class_1263;
import net.minecraft.class_1703;
import net.minecraft.class_1735;
import net.minecraft.class_1746;
import net.minecraft.class_1799;
import net.minecraft.class_1806;
import net.minecraft.class_2561;
import net.minecraft.class_2588;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_465;
import net.minecraft.class_9334;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_465.class)
public abstract class AbstractContainerScreenMixin<T extends class_1703> {
   @Shadow
   @Final
   protected T field_2797;
   @Shadow
   protected class_1735 field_2787;
   @Shadow
   protected int field_2776;

   @Inject(method = "method_2380", at = @At("HEAD"), cancellable = true)
   private void homovore$shulkerPreview(class_332 graphics, int mouseX, int mouseY, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null
         && module.isEnabled()
         && this.field_2787 != null
         && this.field_2787.method_7681()
         && this.field_2797.method_34255().method_7960()
         && module.renderPreview(graphics, this.field_2787.method_7677(), mouseX, mouseY)) {
         ci.cancel();
      }
   }

   @Inject(method = "method_2385", at = @At("TAIL"))
   private void homovore$shulkerIcon(class_332 graphics, class_1735 slot, int mouseX, int mouseY, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled() && slot != null && slot.method_7681()) {
         module.renderIcon(graphics, slot.method_7677(), slot.field_7873, slot.field_7872);
      }
   }

   @Inject(method = "method_71085", at = @At("TAIL"))
   private void homovore$shulkerList(class_332 graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled() && module.shulkerList.getValue() && this.homovore$isEnderChest()) {
         class_310 mc = class_310.method_1551();
         class_1263 playerInventory = mc.field_1724 != null ? mc.field_1724.method_31548() : null;
         List<class_1799> shulkers = new ArrayList<>();

         for (class_1735 slot : this.field_2797.field_7761) {
            if (playerInventory == null || slot.field_7871 != playerInventory) {
               class_1799 stack = slot.method_7677();
               if (ShulkerPreviewModule.isShulkerBox(stack) && stack.method_57826(class_9334.field_49622)) {
                  shulkers.add(stack);
               }
            }
         }

         module.renderShulkerList(graphics, shulkers, this.field_2776);
      }
   }

   private boolean homovore$isEnderChest() {
      class_2561 title = ((class_437)this).method_25440();
      return title != null && title.method_10851() instanceof class_2588 contents && contents.method_11022().equals("container.enderchest");
   }

   @Inject(method = "method_25403", at = @At("HEAD"), cancellable = true)
   private void homovore$swallowDesyncingDrag(class_11909 click, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
      if (FixesModule.swallowsSlotDrag() && class_310.method_1551().field_1724 != null && !class_310.method_1551().field_1724.method_31549().field_7477) {
         class_1799 carried = this.field_2797.method_34255();
         if (carried != null
            && !carried.method_7960()
            && (!carried.method_7946() || carried.method_7909() instanceof class_1806 || carried.method_7909() instanceof class_1746)) {
            cir.setReturnValue(true);
         }
      }
   }
}
