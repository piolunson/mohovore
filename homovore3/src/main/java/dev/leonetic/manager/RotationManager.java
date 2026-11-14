package dev.leonetic.manager;

import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.util.traits.Util;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;

public class RotationManager {
   private static final float SPEED = 360.0F;
   private static final float EASE = 1.0F;
   private static final float THRESHOLD = 5.0F;
   private static final int HOLD_TICKS = 0;
   private static final RotationManager.JitterMode JITTER = RotationManager.JitterMode.NORMAL;
   private static final boolean MOVE_FIX = true;
   private float realYaw;
   private float realPitch;
   private float rotationYaw;
   private float rotationPitch;
   private float rotationYaw0;
   private float rotationPitch0;
   private float serverYaw;
   private float serverPitch;
   private float serverYaw0;
   private float serverDeltaYaw;
   private boolean bypassUseSpoof;
   private boolean silentSyncRequired;
   private boolean silentSentThisTick;
   private int silentSentPriority;
   private long lastSilentMs;
   private final List<RotationRequest> requests = new ArrayList<>();
   private String lastActiveId;
   private float currentYawSpeed;
   private float currentPitchSpeed;
   private int ticksHolding;
   private boolean returning;

   public void init() {
      Util.EVENT_BUS.register(this);
   }

   public void submit(RotationRequest request) {
      if (request.mode == RotationRequest.Mode.SILENT) {
         if (!this.silentSentThisTick || request.priority > this.silentSentPriority) {
            this.performSilent(request);
            this.silentSentThisTick = true;
            this.silentSentPriority = request.priority;
            this.silentSyncRequired = true;
            this.lastSilentMs = System.currentTimeMillis();
         }
      } else {
         this.requests.removeIf(r -> r.id.equals(request.id));
         this.requests.add(request);
         this.requests.sort(Comparator.<RotationRequest>comparingInt(r -> r.priority).reversed());
      }
   }

   public void cancel(String id) {
      this.requests.removeIf(r -> r.id.equals(id));
   }

   public boolean hasRequest(String id) {
      return this.requests.stream().anyMatch(r -> r.id.equals(id));
   }

   public boolean isCompleted(String id) {
      return this.requests
         .stream()
         .filter(r -> r.id.equals(id))
         .findFirst()
         .map(r -> Math.abs(yawDifference(r.targetYaw, this.rotationYaw)) <= 5.0F && Math.abs(r.targetPitch - this.rotationPitch) <= 5.0F)
         .orElse(false);
   }

   @Subscribe(priority = Integer.MIN_VALUE)
   private void onPreTick(PreTickEvent event) {
      if (Util.mc.player != null) {
         this.updateRealRotation(Util.mc.player.getYaw(), Util.mc.player.getPitch());
         this.rotationYaw0 = this.rotationYaw;
         this.rotationPitch0 = this.rotationPitch;
         RotationRequest active = this.getActiveRequest();
         if (active != null) {
            this.processRequest(active);
         } else if (this.returning) {
            this.returnToRealRotation();
         } else {
            this.idleReset();
         }
      }
   }

   private void processRequest(RotationRequest request) {
      if (!request.id.equals(this.lastActiveId)) {
         this.resetRotationToReal();
         this.lastActiveId = request.id;
      }

      boolean updated = false;
      if (request.shouldUpdate()) {
         float oldYaw = request.targetYaw;
         float oldPitch = request.targetPitch;
         request.updateTarget();
         updated = Math.abs(oldYaw - request.targetYaw) > 0.001F || Math.abs(oldPitch - request.targetPitch) > 0.001F;
         if (updated) {
            this.ticksHolding = 0;
         }
      }

      float yawDiff = yawDifference(request.targetYaw, this.rotationYaw);
      float pitchDiff = request.targetPitch - this.rotationPitch;
      boolean reached = Math.abs(yawDiff) <= 5.0F && Math.abs(pitchDiff) <= 5.0F;
      if (!reached || updated) {
         this.ticksHolding = 0;
         this.interpolateRotation(yawDiff, pitchDiff, !request.noJitter);
      } else if (request.sticky) {
         this.ticksHolding = 0;
         this.interpolateRotation(yawDiff, pitchDiff, !request.noJitter);
      } else if (++this.ticksHolding >= 0) {
         this.removeActiveRequest();
         this.ticksHolding = 0;
         this.returning = true;
      }
   }

   private void resetRotationToReal() {
      float targetYaw = alignYaw(this.realYaw, this.rotationYaw);
      this.realYaw = targetYaw;
      Util.mc.player.setYaw(targetYaw);
      this.rotationYaw = this.realYaw;
      this.rotationPitch = this.realPitch;
      this.currentYawSpeed = 0.0F;
      this.currentPitchSpeed = 0.0F;
      this.ticksHolding = 0;
      this.returning = false;
   }

   private void returnToRealRotation() {
      float targetYaw = alignYaw(this.realYaw, this.rotationYaw);
      float yawDiff = targetYaw - this.rotationYaw;
      float pitchDiff = this.realPitch - this.rotationPitch;
      this.interpolateRotation(yawDiff, pitchDiff, true);
      boolean backReached = Math.abs(yawDiff) <= 5.0F && Math.abs(pitchDiff) <= 5.0F;
      if (backReached) {
         this.returning = false;
         this.realYaw = targetYaw;
         Util.mc.player.setYaw(targetYaw);
         this.rotationYaw = this.realYaw;
         this.rotationPitch = this.realPitch;
         this.lastActiveId = null;
      }
   }

   private void interpolateRotation(float yawDiff, float pitchDiff, boolean jitter) {
      this.currentYawSpeed = lerp(this.currentYawSpeed, yawDiff, 1.0F);
      this.currentPitchSpeed = lerp(this.currentPitchSpeed, pitchDiff, 1.0F);
      float yawSpeed = MathHelper.clamp(this.currentYawSpeed, -360.0F, 360.0F);
      float pitchSpeed = MathHelper.clamp(this.currentPitchSpeed, -360.0F, 360.0F);
      float newYaw = this.rotationYaw + yawSpeed;
      float newPitch = this.rotationPitch + pitchSpeed;
      if (jitter && JITTER == RotationManager.JitterMode.NORMAL) {
         float minJitter = 1.25F;
         float maxJitter = 2.5F;
         float jitterYaw = minJitter + (float)(Math.random() * (maxJitter - minJitter));
         float jitterPitch = minJitter + (float)(Math.random() * (maxJitter - minJitter));
         jitterYaw *= Math.random() < 0.5 ? -1.0F : 1.0F;
         jitterPitch *= Math.random() < 0.5 ? -1.0F : 1.0F;
         newYaw += jitterYaw;
         newPitch = MathHelper.clamp(newPitch + jitterPitch, -90.0F, 90.0F);
      } else if (jitter && JITTER == RotationManager.JitterMode.GRIM) {
         float f = (float)((Math.random() * 2.0 - 1.0) * 0.001F);
         newPitch = MathHelper.clamp(newPitch + f, -90.0F, 90.0F);
      }

      this.rotationYaw = newYaw;
      this.rotationPitch = MathHelper.clamp(newPitch, -90.0F, 90.0F);
   }

   private void idleReset() {
      this.lastActiveId = null;
      this.rotationYaw = this.realYaw;
      this.rotationPitch = this.realPitch;
      this.currentYawSpeed = 0.0F;
      this.currentPitchSpeed = 0.0F;
   }

   public PlayerInput computeMoveFixInput(PlayerInput real, float realYawNow) {
      float delta = MathHelper.wrapDegrees(realYawNow - this.rotationYaw);
      float inputX = (real.right() ? 1 : 0) - (real.left() ? 1 : 0);
      float inputZ = (real.forward() ? 1 : 0) - (real.backward() ? 1 : 0);
      if (inputX == 0.0F && inputZ == 0.0F) {
         return real;
      }

      double moveAngle = Math.toDegrees(Math.atan2(inputX, inputZ));
      double finalAngle = moveAngle + delta;
      int sector = (int)Math.round(finalAngle / 45.0) & 7;
      boolean f = false;
      boolean b = false;
      boolean l = false;
      boolean r = false;
      switch (sector) {
         case 0:
            f = true;
            break;
         case 1:
            f = true;
            r = true;
            break;
         case 2:
            r = true;
            break;
         case 3:
            b = true;
            r = true;
            break;
         case 4:
            b = true;
            break;
         case 5:
            b = true;
            l = true;
            break;
         case 6:
            l = true;
            break;
         case 7:
            f = true;
            l = true;
      }

      return new PlayerInput(f, b, l, r, real.jump(), real.sneak(), real.sprint());
   }

   private void performSilent(RotationRequest req) {
      float targetYaw = req.targetYaw;
      float targetPitch = req.targetPitch;
      float f = (float)((Math.random() * 2.0 - 1.0) * 0.001F);
      targetPitch = MathHelper.clamp(targetPitch + f, -90.0F, 90.0F);
      this.setServerYaw(targetYaw);
      this.setServerPitch(targetPitch);
      Util.mc
         .getNetworkHandler()
         .sendPacket(new Full(Util.mc.player.getX(), Util.mc.player.getY(), Util.mc.player.getZ(), targetYaw, targetPitch, Util.mc.player.isOnGround(), true));
   }

   private RotationRequest getActiveRequest() {
      return this.requests.isEmpty() ? null : this.requests.get(0);
   }

   private void removeActiveRequest() {
      if (!this.requests.isEmpty()) {
         this.requests.remove(0);
      }
   }

   private void updateRealRotation(float yaw, float pitch) {
      this.realYaw = yaw;
      this.realPitch = MathHelper.clamp(pitch, -90.0F, 90.0F);
   }

   public boolean isRotating() {
      return this.getActiveRequest() != null || this.returning;
   }

   public boolean isActive(String id) {
      RotationRequest active = this.getActiveRequest();
      return active != null && active.id.equals(id);
   }

   public boolean isMoveFixEnabled() {
      return true;
   }

   public float getRealYaw() {
      return this.realYaw;
   }

   public float getRealPitch() {
      return this.realPitch;
   }

   public float getRotationYaw() {
      return this.rotationYaw;
   }

   public float getRotationPitch() {
      return this.rotationPitch;
   }

   public float getRenderYaw(float partialTicks) {
      return MathHelper.lerpAngleDegrees(partialTicks, this.rotationYaw0, this.rotationYaw);
   }

   public float getRenderPitch(float partialTicks) {
      return this.rotationPitch0 + (this.rotationPitch - this.rotationPitch0) * partialTicks;
   }

   public float getServerYaw() {
      return this.serverYaw;
   }

   public float getServerPitch() {
      return this.serverPitch;
   }

   public float getServerYaw0() {
      return this.serverYaw0;
   }

   public float getServerDeltaYaw() {
      return this.serverDeltaYaw;
   }

   public void setServerYaw(float yaw) {
      this.serverYaw0 = this.serverYaw;
      this.serverYaw = yaw;
   }

   public void setServerPitch(float pitch) {
      this.serverPitch = pitch;
   }

   public void setServerDeltaYaw(float delta) {
      this.serverDeltaYaw = delta;
   }

   public boolean isBypassUseSpoof() {
      return this.bypassUseSpoof;
   }

   public void setBypassUseSpoof(boolean bypass) {
      this.bypassUseSpoof = bypass;
   }

   public boolean isSilentSyncRequired() {
      return this.silentSyncRequired;
   }

   public boolean isSilentSyncRequiredAtLeast(int priority) {
      return this.silentSyncRequired && this.silentSentPriority >= priority;
   }

   public void setSilentSyncRequired(boolean required) {
      this.silentSyncRequired = required;
   }

   public void resetSilentTick() {
      this.silentSentThisTick = false;
   }

   public boolean isSilentActive() {
      return System.currentTimeMillis() - this.lastSilentMs < 500L;
   }

   private static float yawDifference(float target, float current) {
      return MathHelper.wrapDegrees(target - current);
   }

   private static float alignYaw(float base, float reference) {
      int wraps = Math.round((reference - base) / 360.0F);
      return base + wraps * 360.0F;
   }

   private static float lerp(float from, float to, float factor) {
      return from + (to - from) * factor;
   }

   public enum JitterMode {
      NONE,
      GRIM,
      NORMAL;
   }
}
