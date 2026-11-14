package dev.leonetic.features.modules.funny;

import dev.leonetic.Homovore;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class DiscordRPCModule extends Module {
   private static final long APP_ID = 1497806002139955270L;
   private final Setting<DiscordRPCModule.LineMode> line1Mode = this.mode("Line1Mode", DiscordRPCModule.LineMode.SERVER).setPage("General");
   private final Setting<String> line1Custom = this.str("Line1Custom", "Owning on [server]").setPage("General");
   private final Setting<DiscordRPCModule.LineMode> line2Mode = this.mode("Line2Mode", DiscordRPCModule.LineMode.STATS).setPage("General");
   private final Setting<String> line2Custom = this.str("Line2Custom", "HP: [health] | Ping: [ping]ms").setPage("General");
   private final Setting<Boolean> hideIp = this.bool("HideIP", false).setPage("General");
   private final Setting<Integer> antileak = this.num("AntiLeakRadius", 50000, 1000, 100000).setPage("General");
   private static final RichPresence rpc = new RichPresence();
   private int ticks;

   public DiscordRPCModule() {
      super("DiscordRPC", "Shows a highly customizable Discord Rich Presence.", Module.Category.FUNNY);
   }

   @Override
   public void onEnable() {
      DiscordIPC.start(1497806002139955270L, null);
      rpc.setStart(System.currentTimeMillis() / 1000L);
      rpc.setLargeImage("icon", "Homovore Client");
      this.ticks = 0;
   }

   @Override
   public void onDisable() {
      DiscordIPC.stop();
   }

   @Override
   public void onTick() {
      if (++this.ticks >= 20) {
         this.ticks = 0;
         if (!nullCheck()) {
            rpc.setDetails(this.formatLine(this.line1Mode.getValue(), this.line1Custom.getValue()));
            rpc.setState(this.formatLine(this.line2Mode.getValue(), this.line2Custom.getValue()));
            if (mc.player != null) {
               String name = mc.player.getName().getString();
               rpc.setSmallImage("https://minotar.net/helm/" + name + "/100.png", name);
            }

            DiscordIPC.setActivity(rpc);
         }
      }
   }

   private String formatLine(DiscordRPCModule.LineMode mode, String customFormat) {
      return switch (mode) {
         case CUSTOM -> this.applyTokens(customFormat);
         case SERVER -> "Playing on " + this.getServerIp();
         case DIMENSION -> this.getDimensionText() + " " + this.getCoordsText();
         case HEALTH -> "Health: " + this.getHealth() + " | Ping: " + this.getPing() + "ms";
         case STATS -> "Totems: " + this.getTotems() + " | Modules: " + this.getActiveModules();
      };
   }

   private String applyTokens(String text) {
      return text == null
         ? ""
         : text.replace("[server]", this.getServerIp())
            .replace("[ping]", String.valueOf(this.getPing()))
            .replace("[health]", this.getHealth())
            .replace("[dimension]", this.getDimensionText())
            .replace("[coords]", this.getCoordsText())
            .replace("[totems]", String.valueOf(this.getTotems()))
            .replace("[modules]", String.valueOf(this.getActiveModules()));
   }

   private String getServerIp() {
      if (mc.isIntegratedServerRunning()) {
         return "Singleplayer";
      } else if (mc.getCurrentServerEntry() != null) {
         return this.hideIp.getValue() ? "Private Server" : mc.getCurrentServerEntry().address;
      } else {
         return "Main Menu";
      }
   }

   private String getDimensionText() {
      if (mc.player == null) {
         return "Unknown";
      }

      String dimPath = mc.player.getEntityWorld().getRegistryKey().getValue().getPath();

      return switch (dimPath) {
         case "overworld" -> "Overworld";
         case "the_nether" -> "The Nether";
         case "the_end" -> "The End";
         default -> dimPath;
      };
   }

   private String getCoordsText() {
      if (mc.player == null) {
         return "";
      }

      int radius = this.antileak.getValue();
      return Math.abs(mc.player.getBlockX()) <= radius && Math.abs(mc.player.getBlockZ()) <= radius
         ? String.format("[%d, %d, %d]", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())
         : "[x, y, z]";
   }

   private String getHealth() {
      if (mc.player == null) {
         return "0.0";
      }

      float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
      return String.format("%.1f", hp);
   }

   private int getPing() {
      if (mc.player != null && mc.getNetworkHandler() != null) {
         PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
         return info == null ? 0 : info.getLatency();
      } else {
         return 0;
      }
   }

   private int getTotems() {
      if (mc.player == null) {
         return 0;
      }

      int count = 0;

      for (int i = 0; i < mc.player.getInventory().size(); i++) {
         ItemStack stack = mc.player.getInventory().getStack(i);
         if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
            count += stack.getCount();
         }
      }

      return count;
   }

   private int getActiveModules() {
      return (int)Homovore.moduleManager.getModules().stream().filter(Module::isEnabled).count();
   }

   public enum LineMode {
      CUSTOM,
      SERVER,
      DIMENSION,
      HEALTH,
      STATS;
   }
}
