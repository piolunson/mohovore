package dev.piolunson.mixin.client;

import dev.piolunson.features.modules.render.ShulkerPreviewModule;
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
public class MixinAbstractContainerScreen<T extends ScreenHandler> {
   @Shadow
   @Final
   protected T handler;
   @Shadow
   protected Slot focusedSlot;
   @Shadow
   protected int x;

   @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
   private void onRenderShulkerPreview(DrawContext graphics, int mouseX, int mouseY, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled()) {
         if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
            if (this.handler.getCursorStack().isEmpty()) {
               if (module.renderPreview(graphics, this.focusedSlot.getStack(), mouseX, mouseY)) {
                  ci.cancel();
               }
            }
         }
      }
   }

   @Inject(method = "renderMain", at = @At("TAIL"))
   private void onRenderShulkerList(DrawContext graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled() && module.shulkerList.getValue()) {
         if (this.isEnderChestScreen()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            Inventory playerInv = mc.player != null ? mc.player.getInventory() : null;
            List<ItemStack> shulkers = new ArrayList<>();

            for (Slot slot : this.handler.slots) {
               if (playerInv == null || slot.inventory != playerInv) {
                  ItemStack stack = slot.getStack();
                  if (ShulkerPreviewModule.isShulkerBox(stack) && stack.contains(DataComponentTypes.CONTAINER)) {
                     shulkers.add(stack);
                  }
               }
            }

            module.renderShulkerList(graphics, shulkers, this.x);
         }
      }
   }

   private boolean isEnderChestScreen() {
      Text title = ((Screen)(Object)this).getTitle();
      return title != null && title.getContent() instanceof TranslatableTextContent contents && contents.getKey().equals("container.enderchest");
   }

   @Inject(method = "drawSlot", at = @At("TAIL"))
   private void onRenderShulkerThumbnail(DrawContext graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
      ShulkerPreviewModule module = ShulkerPreviewModule.get();
      if (module != null && module.isEnabled()) {
         if (slot != null && slot.hasStack()) {
            module.renderThumbnail(graphics, slot.getStack(), slot.x, slot.y);
         }
      }
   }

   @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
   private void onMouseDragged(Click mouseButtonEvent, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null || !mc.player.getAbilities().creativeMode) {
         ItemStack cursorStack = this.handler.getCursorStack();
         if (cursorStack != null && !cursorStack.isEmpty()) {
            if (!cursorStack.isStackable() || cursorStack.getItem() instanceof FilledMapItem || cursorStack.getItem() instanceof BannerItem) {
               cir.setReturnValue(true);
            }
         }
      }
   }
}
