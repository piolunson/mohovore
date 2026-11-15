package dev.piolunson.v2.mixin;

import dev.piolunson.v2.feature.module.impl.FixesModule;
import dev.piolunson.v2.feature.module.impl.ShulkerPreviewModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BannerItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class AbstractContainerScreenMixin<T extends ScreenHandler> {
   @Shadow
   @Final
   protected T handler;
   @Shadow
   protected Slot focusedSlot;
   @Shadow
   protected int x;

   @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
   private void homovore$shulkerPreview(DrawContext graphics, int mouseX, int mouseY, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null
         && module.isEnabled()
         && this.focusedSlot != null
         && this.focusedSlot.hasStack()
         && this.handler.getCursorStack().isEmpty()
         && module.renderPreview(graphics, this.focusedSlot.getStack(), mouseX, mouseY)) {
         ci.cancel();
      }
   }

   @Inject(method = "drawSlot", at = @At("TAIL"))
   private void homovore$shulkerIcon(DrawContext graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled() && slot != null && slot.hasStack()) {
         module.renderIcon(graphics, slot.getStack(), slot.x, slot.y);
      }
   }

   @Inject(method = "renderMain", at = @At("TAIL"))
   private void homovore$shulkerList(DrawContext graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled() && module.shulkerList.getValue() && this.homovore$isEnderChest()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         Inventory playerInventory = mc.player != null ? mc.player.getInventory() : null;
         List<ItemStack> shulkers = new ArrayList<>();

         for (Slot slot : this.handler.slots) {
            if (playerInventory == null || slot.inventory != playerInventory) {
               ItemStack stack = slot.getStack();
               if (ShulkerPreviewModule.isShulkerBox(stack) && stack.contains(DataComponentTypes.CONTAINER)) {
                  shulkers.add(stack);
               }
            }
         }

         module.renderShulkerList(graphics, shulkers, this.x);
      }
   }

   private boolean homovore$isEnderChest() {
      Text title = ((Screen)(Object)this).getTitle();
      return title != null && title.getContent() instanceof TranslatableTextContent contents && contents.getKey().equals("container.enderchest");
   }

   @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
   private void homovore$swallowDesyncingDrag(Click click, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
      if (FixesModule.swallowsSlotDrag() && MinecraftClient.getInstance().player != null && !MinecraftClient.getInstance().player.getAbilities().creativeMode) {
         ItemStack carried = this.handler.getCursorStack();
         if (carried != null
            && !carried.isEmpty()
            && (!carried.isStackable() || carried.getItem() instanceof FilledMapItem || carried.getItem() instanceof BannerItem)) {
            cir.setReturnValue(true);
         }
      }
   }
}
