package dev.leonetic.features.modules.combat;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.TickEvent;
import dev.leonetic.event.impl.input.KeyInputEvent;
import dev.leonetic.event.impl.network.PacketEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Bind;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.mixin.network.ConnectionAccessor;
import io.netty.channel.Channel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1934;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_2663;
import net.minecraft.class_2749;

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
         if (mc.field_1755 == null) {
            if (mc.field_1724.method_29504()) {
               this.disable();
            } else {
               this.updateTotemCount();
               this.nearbyPlayerNames = mc.field_1687.method_18456().stream().filter(p -> p != mc.field_1724).map(p -> p.method_7334().name()).toList();
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
         if (event.getPacket() instanceof class_2663 pkt) {
            if (pkt.method_11470() == 35 && pkt.method_11469(mc.field_1687) == mc.field_1724) {
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
         } else if (event.getPacket() instanceof class_2749 pkt) {
            if (pkt.method_11833() <= 0.0F) {
               this.disable();
               return;
            }

            if (this.logOnHealth.getValue() && pkt.method_11833() <= this.healthThreshold.getValue().intValue()) {
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
               boolean offhandHasTotem = mc.field_1724.method_6079().method_31574(class_1802.field_8288);
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
         float hp = mc.field_1724.method_6032() + mc.field_1724.method_6067();
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
         class_1799 stack = mc.field_1724.method_31548().method_5438(i);
         if (stack.method_31574(class_1802.field_8288)) {
            count += stack.method_7947();
         }
      }

      class_1799 offhand = mc.field_1724.method_6079();
      if (offhand.method_31574(class_1802.field_8288)) {
         count += offhand.method_7947();
      }

      this.cachedTotems = count;
   }

   private Channel getChannel() {
      return mc.method_1562() == null ? null : ((ConnectionAccessor)mc.method_1562().method_48296()).getChannel();
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
      return mc.field_1761 != null && mc.field_1761.method_2920() == class_1934.field_9215;
   }

   private void disconnect() {
      if (!nullCheck() && mc.method_1562() != null) {
         class_2338 pos = mc.field_1724.method_24515();
         List<String> nearby = new ArrayList<>(this.nearbyPlayerNames);
         StringBuilder msg = new StringBuilder("AutoLog Disconnect");
         msg.append("\nTotems: ").append(this.cachedTotems);
         msg.append("\nCoords: ").append(pos.method_10263()).append(", ").append(pos.method_10264()).append(", ").append(pos.method_10260());
         msg.append(nearby.isEmpty() ? "\nNearby: none" : "\nNearby: " + String.join(", ", nearby));
         mc.method_1562().method_48296().method_10747(class_2561.method_43470(msg.toString()));
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
