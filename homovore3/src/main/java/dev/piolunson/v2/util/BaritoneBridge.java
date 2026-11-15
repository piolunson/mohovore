package dev.piolunson.v2.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.util.math.BlockPos;

final class BaritoneBridge {
   private static Object pauseProcess;
   private static boolean paused;

   private BaritoneBridge() {
   }

   static void setPaused(boolean paused) {
      if (pauseProcess == null) {
         if (!paused) {
            return;
         }

         Class<?> processType = load("baritone.api.process.IBaritoneProcess");
         InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "isActive" -> paused;
            case "onTick" -> {
               Object baritone = primaryBaritone();
               call(call(baritone, "getInputOverrideHandler"), "clearAllKeys");
               yield pauseCommand();
            }
            case "isTemporary" -> true;
            case "onLostControl" -> null;
            case "priority" -> 0.0;
            case "displayName0" -> "Homovore";
            case "toString" -> "HomovorePauseProcess";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new UnsupportedOperationException(method.getName());
         };
         pauseProcess = Proxy.newProxyInstance(processType.getClassLoader(), new Class[]{processType}, handler);
         Object baritone = primaryBaritone();
         call(call(baritone, "getPathingControlManager"), "registerProcess", new Class[]{processType}, pauseProcess);
      }

      BaritoneBridge.paused = paused;
   }

   static boolean isPaused() {
      return pauseProcess != null && paused;
   }

   static boolean isPathing() {
      Object baritone = primaryBaritone();
      Object goalProcess = call(baritone, "getCustomGoalProcess");
      return call(goalProcess, "getGoal") != null || Boolean.TRUE.equals(call(call(baritone, "getPathingBehavior"), "isPathing"));
   }

   static void pathTo(BlockPos pos) {
      try {
         Class<?> goalType = load("baritone.api.pathing.goals.Goal");
         Class<?> goalBlockType = load("baritone.api.pathing.goals.GoalBlock");
         Constructor<?> constructor = goalBlockType.getConstructor(BlockPos.class);
         Object goal = constructor.newInstance(pos);
         call(call(primaryBaritone(), "getCustomGoalProcess"), "setGoalAndPath", new Class[]{goalType}, goal);
      } catch (ReflectiveOperationException exception) {
         throw new IllegalStateException("Could not create a Baritone block goal", exception);
      }
   }

   static void clearGoal() {
      Object baritone = primaryBaritone();
      if (call(call(baritone, "getElytraProcess"), "currentDestination") == null) {
         Class<?> goalType = load("baritone.api.pathing.goals.Goal");
         call(call(baritone, "getCustomGoalProcess"), "setGoal", new Class[]{goalType}, null);
         call(call(baritone, "getPathingBehavior"), "cancelEverything");
      }
   }

   private static Object primaryBaritone() {
      return call(callStatic(load("baritone.api.BaritoneAPI"), "getProvider"), "getPrimaryBaritone");
   }

   private static Object pauseCommand() {
      try {
         Class<?> goalType = load("baritone.api.pathing.goals.Goal");
         Class<?> commandType = load("baritone.api.process.PathingCommandType");
         Class<?> commandClass = load("baritone.api.process.PathingCommand");
         Object requestPause = Enum.valueOf((Class) commandType, "REQUEST_PAUSE");
         Constructor<?> constructor = commandClass.getConstructor(goalType, commandType);
         return constructor.newInstance(null, requestPause);
      } catch (ReflectiveOperationException exception) {
         throw new IllegalStateException("Could not create a Baritone pause command", exception);
      }
   }

   private static Object call(Object target, String name) {
      return call(target, name, new Class[0]);
   }

   private static Object call(Object target, String name, Class<?>[] parameterTypes, Object... arguments) {
      try {
         Method method = target.getClass().getMethod(name, parameterTypes);
         method.setAccessible(true);
         return method.invoke(target, arguments);
      } catch (ReflectiveOperationException exception) {
         throw new IllegalStateException("Could not call Baritone method " + name, exception);
      }
   }

   private static Object callStatic(Class<?> target, String name) {
      try {
         return target.getMethod(name).invoke(null);
      } catch (ReflectiveOperationException exception) {
         throw new IllegalStateException("Could not call Baritone method " + name, exception);
      }
   }

   private static Class<?> load(String name) {
      try {
         return Class.forName(name);
      } catch (ClassNotFoundException exception) {
         throw new IllegalStateException("Baritone API class is unavailable: " + name, exception);
      }
   }
}
