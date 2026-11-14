package dev.leonetic.util.inventory;

import java.util.List;
import net.minecraft.class_1799;
import net.minecraft.class_243;

public final class InventorySnapshot {
   static final InventorySnapshot EMPTY = new InventorySnapshot(
      0L,
      -1,
      -1,
      -1,
      -1,
      false,
      false,
      false,
      false,
      false,
      false,
      0.0,
      0.0,
      0.0,
      0.0,
      new double[0],
      1.0F,
      0.0F,
      0.0F,
      false,
      false,
      List.of(),
      class_1799.field_8037,
      class_1799.field_8037,
      new int[0]
   );
   private final long version;
   private final int selectedSlot;
   private final int playerId;
   private final int menuId;
   private final int menuStateId;
   private final boolean usingItem;
   private final boolean usingMainHand;
   private final boolean deadOrDying;
   private final boolean sprinting;
   private final boolean shiftKeyDown;
   private final boolean flying;
   private final double x;
   private final double y;
   private final double z;
   private final double eyeY;
   private final double[] possibleEyeHeights;
   private final float estimatedAttackDamage;
   private final class_1799 cursor;
   private final float yaw;
   private final float pitch;
   private final boolean onGround;
   private final boolean horizontalCollision;
   private final List<class_1799> items;
   private final class_1799 offhand;
   private final int[] menuSlots;

   InventorySnapshot(
      long version,
      int selectedSlot,
      int playerId,
      int menuId,
      int menuStateId,
      boolean usingItem,
      boolean usingMainHand,
      boolean deadOrDying,
      boolean sprinting,
      boolean shiftKeyDown,
      boolean flying,
      double x,
      double y,
      double z,
      double eyeY,
      double[] possibleEyeHeights,
      float estimatedAttackDamage,
      float yaw,
      float pitch,
      boolean onGround,
      boolean horizontalCollision,
      List<class_1799> items,
      class_1799 cursor,
      class_1799 offhand,
      int[] menuSlots
   ) {
      this.version = version;
      this.selectedSlot = selectedSlot;
      this.playerId = playerId;
      this.menuId = menuId;
      this.menuStateId = menuStateId;
      this.usingItem = usingItem;
      this.usingMainHand = usingMainHand;
      this.deadOrDying = deadOrDying;
      this.sprinting = sprinting;
      this.shiftKeyDown = shiftKeyDown;
      this.flying = flying;
      this.x = x;
      this.y = y;
      this.z = z;
      this.eyeY = eyeY;
      this.possibleEyeHeights = (double[])possibleEyeHeights.clone();
      this.estimatedAttackDamage = estimatedAttackDamage;
      this.cursor = cursor.method_7972();
      this.yaw = yaw;
      this.pitch = pitch;
      this.onGround = onGround;
      this.horizontalCollision = horizontalCollision;
      this.items = items;
      this.offhand = offhand.method_7972();
      this.menuSlots = (int[])menuSlots.clone();
   }

   public long version() {
      return this.version;
   }

   public boolean available() {
      return this.selectedSlot >= 0;
   }

   public int selectedSlot() {
      return this.selectedSlot;
   }

   public int playerId() {
      return this.playerId;
   }

   public int menuId() {
      return this.menuId;
   }

   public int menuStateId() {
      return this.menuStateId;
   }

   public boolean usingItem() {
      return this.usingItem;
   }

   public boolean usingMainHand() {
      return this.usingMainHand;
   }

   public boolean deadOrDying() {
      return this.deadOrDying;
   }

   public boolean sprinting() {
      return this.sprinting;
   }

   public boolean shiftKeyDown() {
      return this.shiftKeyDown;
   }

   public boolean flying() {
      return this.flying;
   }

   public boolean cursorEmpty() {
      return this.cursor.method_7960();
   }

   public class_1799 cursor() {
      return this.cursor.method_7972();
   }

   public float yaw() {
      return this.yaw;
   }

   public float pitch() {
      return this.pitch;
   }

   public double x() {
      return this.x;
   }

   public double y() {
      return this.y;
   }

   public double z() {
      return this.z;
   }

   public double eyeY() {
      return this.eyeY;
   }

   public class_243 eyePosition() {
      return new class_243(this.x, this.eyeY, this.z);
   }

   public class_243[] possibleEyePositions() {
      class_243[] positions = new class_243[this.possibleEyeHeights.length];

      for (int i = 0; i < this.possibleEyeHeights.length; i++) {
         positions[i] = new class_243(this.x, this.y + this.possibleEyeHeights[i], this.z);
      }

      return positions;
   }

   public float estimatedAttackDamage() {
      return this.estimatedAttackDamage;
   }

   public boolean onGround() {
      return this.onGround;
   }

   public boolean horizontalCollision() {
      return this.horizontalCollision;
   }

   public int size() {
      return this.items.size();
   }

   public class_1799 item(int slot) {
      return slot >= 0 && slot < this.items.size() ? this.items.get(slot).method_7972() : class_1799.field_8037;
   }

   public class_1799 offhand() {
      return this.offhand.method_7972();
   }

   public int menuSlot(int inventorySlot) {
      return inventorySlot >= 0 && inventorySlot < this.menuSlots.length ? this.menuSlots[inventorySlot] : -1;
   }

   int inventorySlot(int menuSlot) {
      for (int inventorySlot = 0; inventorySlot < this.menuSlots.length; inventorySlot++) {
         if (this.menuSlots[inventorySlot] == menuSlot) {
            return inventorySlot;
         }
      }

      return -1;
   }

   class_1799 itemInternal(int slot) {
      return slot >= 0 && slot < this.items.size() ? this.items.get(slot) : class_1799.field_8037;
   }

   class_1799 offhandInternal() {
      return this.offhand;
   }
}
