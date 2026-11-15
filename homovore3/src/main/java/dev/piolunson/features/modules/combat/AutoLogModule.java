package dev.piolunson.features.modules.combat;

import dev.piolunson.Homovore;
import dev.piolunson.event.impl.entity.player.TickEvent;
import dev.piolunson.event.impl.input.KeyInputEvent;
import dev.piolunson.event.impl.network.PacketEvent;
import dev.piolunson.event.system.Subscribe;
import dev.piolunson.features.modules.Module;
import dev.piolunson.features.settings.Bind;
import dev.piolunson.features.settings.Setting;
import dev.piolunson.mixin.network.ConnectionAccessor;
import io.netty.channel.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.HealthUpdateS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

public class AutoLogModule extends Module {
   private final Setting<Boolean> logOnHealth = this.bool("LogOnHealth", true);
   private final Setting<Integer> healthThreshold = this.num("HealthThreshold", 6, 1, 36);
   private final Setting<Boolean> logOnTotems = this.bool("LogOnTotems", true);
   private final Setting<Integer> totemThreshold = this.num("TotemThreshold", 1, 0, 64);
   private final Setting<Bind> disconnectBind = this.key("DisconnectBind", Bind.none());
   private int cachedTotems = 0;
   private volatile boolean awaitingRechamber = false;
   private List<String> nearbyPlayerNames = new ArrayList<>();

   public AutoLogModule() {
      super("AutoLog", "Automatically disconnects based on health or totem count.", Module.Category.COMBAT);
      this.healthThreshold.setVisibility(v -> this.logOnHealth.getValue());
      this.totemThreshold.setVisibility(v -> this.logOnTotems.getValue());
   }

   @Override
   public void onEnable() {
      this.awaitingRechamber = false;
      if (!nullCheck()) {
         this.updateTotemCount();
      }
   }

   @Override
   public void onDisable() {
      this.awaitingRechamber = false;
   }

   @Subscribe
   private void onTick(TickEvent event) {
      if (!nullCheck()) {
         if (mc.currentScreen == null) {
            if (mc.player.isDead()) {
               this.disable();
            } else {
               this.updateTotemCount();
               this.nearbyPlayerNames = mc.world.getPlayers().stream().filter(p -> p != mc.player).map(p -> p.getGameProfile().name()).toList();
               if (!this.awaitingRechamber && this.isSurvival()) {
                  this.checkThresholds();
               }
            }
         }
      }
   }

   @Subscribe
   private void onPacketReceive(PacketEvent.Receive event) {
      if (!nullCheck() && this.isSurvival()) {
         if (event.getPacket() instanceof EntityStatusS2CPacket pkt) {
            if (pkt.getStatus() == 35 && pkt.getEntity(mc.world) == mc.player) {
               this.cachedTotems--;
               if (!this.logOnTotems.getValue() || this.cachedTotems > this.totemThreshold.getValue()) {
                  return;
               }

               if (this.awaitingRechamber) {
                  this.awaitingRechamber = false;
                  mc.execute(this::disconnect);
                  return;
               }

               boolean pollOffhand = this.totemThreshold.getValue() > 1 && this.isAutoTotemEnabled();
               Channel channel = pollOffhand ? this.getChannel() : null;
               if (channel == null) {
                  this.disconnect();
                  return;
               }

               this.awaitingRechamber = true;
               this.pollForRechamber(channel, System.currentTimeMillis() + 500L);
            }
         } else if (event.getPacket() instanceof HealthUpdateS2CPacket pkt) {
            if (pkt.getHealth() <= 0.0F) {
               this.disable();
               return;
            }

            if (this.logOnHealth.getValue() && pkt.getHealth() <= this.healthThreshold.getValue().intValue()) {
               this.disconnect();
            }
         }
      }
   }

   @Subscribe
   private void onKeyInput(KeyInputEvent event) {
      if (!this.disconnectBind.getValue().isEmpty() && event.getAction() == 1) {
         if (event.getKey() == this.disconnectBind.getValue().getKey() && !nullCheck() && this.isSurvival()) {
            this.disconnect();
         }
      }
   }

   private void pollForRechamber(Channel channel, long deadline) {
      if (!channel.isOpen()) {
         this.awaitingRechamber = false;
      } else {
         channel.eventLoop().schedule(() -> mc.execute(() -> {
            if (this.awaitingRechamber && !nullCheck() && this.isSurvival()) {
               this.updateTotemCount();
               boolean offhandHasTotem = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
               boolean noTotems = this.cachedTotems == 0;
               boolean timedOut = System.currentTimeMillis() >= deadline;
               if (!offhandHasTotem && !noTotems && !timedOut) {
                  this.pollForRechamber(channel, deadline);
               } else {
                  this.awaitingRechamber = false;
                  if (this.logOnTotems.getValue() && this.cachedTotems <= this.totemThreshold.getValue()) {
                     this.disconnect();
                  }
               }
            } else {
               this.awaitingRechamber = false;
            }
         }), 10L, TimeUnit.MILLISECONDS);
      }
   }

   private void checkThresholds() {
      if (this.logOnHealth.getValue()) {
         float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
         if (hp <= this.healthThreshold.getValue().intValue()) {
            this.disconnect();
            return;
         }
      }

      if (this.logOnTotems.getValue() && this.cachedTotems <= this.totemThreshold.getValue()) {
         this.disconnect();
      }
   }

   private void updateTotemCount() {
      int count = 0;

      for (int i = 0; i < 36; i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
            count += stack.getCount();
         }
      }

      ItemStack offhand = mc.player.getOffHandStack();
      if (offhand.isOf(Items.TOTEM_OF_UNDYING)) {
         count += offhand.getCount();
      }

      this.cachedTotems = count;
   }

   private Channel getChannel() {
      return mc.getNetworkHandler() == null ? null : ((ConnectionAccessor)mc.getNetworkHandler().getConnection()).getChannel();
   }

   private void closeChannel() {
      Channel channel = this.getChannel();
      if (channel != null) {
         channel.close();
      }
   }

   private boolean isAutoTotemEnabled() {
      AutoTotemModule autoTotem = Homovore.moduleManager.getModuleByClass(AutoTotemModule.class);
      return autoTotem != null && autoTotem.isEnabled();
   }

   private boolean isSurvival() {
      return mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() == GameMode.SURVIVAL;
   }

   private void disconnect() {
      if (!nullCheck() && mc.getNetworkHandler() != null) {
         BlockPos pos = mc.player.getBlockPos();
         List<String> nearby = new ArrayList<>(this.nearbyPlayerNames);
         StringBuilder msg = new StringBuilder("AutoLog Disconnect");
         msg.append("\nTotems: ").append(this.cachedTotems);
         msg.append("\nCoords: ").append(pos.getX()).append(", ").append(pos.getY()).append(", ").append(pos.getZ());
         msg.append(nearby.isEmpty() ? "\nNearby: none" : "\nNearby: " + String.join(", ", nearby));
         mc.getNetworkHandler().getConnection().disconnect(Text.literal(msg.toString()));
      } else {
         this.closeChannel();
      }

      this.disable();
   }

   @Override
   public String getDisplayInfo() {
      return nullCheck() ? null : String.valueOf(this.cachedTotems);
   }
}
