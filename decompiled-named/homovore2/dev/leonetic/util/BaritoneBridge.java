package dev.leonetic.util;

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
         InvocationHandler handler = (proxy, method, args) -> {
            // $VF: Couldn't be decompiled
            // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
            // java.lang.RuntimeException: invalid constant type: Ljava/lang/Object; with value Homovore
            //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent.toJava(ConstExprent.java:364)
            //   at org.jetbrains.java.decompiler.modules.decompiler.exps.SwitchExprent.toJava(SwitchExprent.java:152)
            //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1054)
            //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent.toJava(ExitExprent.java:85)
            //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
            //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
            //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:860)
            //   at org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement.toJava(SequenceStatement.java:107)
            //   at org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement.toJava(RootStatement.java:36)
            //   at org.jetbrains.java.decompiler.main.ClassWriter.methodLambdaToJava(ClassWriter.java:1017)
            //
            // Bytecode:
            // 000: aload 1
            // 001: invokevirtual java/lang/reflect/Method.getName ()Ljava/lang/String;
            // 004: astore 3
            // 005: bipush -1
            // 006: istore 4
            // 008: aload 3
            // 009: invokevirtual java/lang/String.hashCode ()I
            // 00c: lookupswitch 223 9 -1776922004 175 -1295482945 209 -1165461084 144 -1131560889 114 -1012937700 99 -748916528 84 147696667 192 563621434 129 1599010659 159
            // 060: aload 3
            // 061: ldc "isActive"
            // 063: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 066: ifeq 0eb
            // 069: bipush 0
            // 06a: istore 4
            // 06c: goto 0eb
            // 06f: aload 3
            // 070: ldc "onTick"
            // 072: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 075: ifeq 0eb
            // 078: bipush 1
            // 079: istore 4
            // 07b: goto 0eb
            // 07e: aload 3
            // 07f: ldc "isTemporary"
            // 081: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 084: ifeq 0eb
            // 087: bipush 2
            // 088: istore 4
            // 08a: goto 0eb
            // 08d: aload 3
            // 08e: ldc "onLostControl"
            // 090: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 093: ifeq 0eb
            // 096: bipush 3
            // 097: istore 4
            // 099: goto 0eb
            // 09c: aload 3
            // 09d: ldc "priority"
            // 09f: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 0a2: ifeq 0eb
            // 0a5: bipush 4
            // 0a6: istore 4
            // 0a8: goto 0eb
            // 0ab: aload 3
            // 0ac: ldc_w "displayName0"
            // 0af: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 0b2: ifeq 0eb
            // 0b5: bipush 5
            // 0b6: istore 4
            // 0b8: goto 0eb
            // 0bb: aload 3
            // 0bc: ldc_w "toString"
            // 0bf: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 0c2: ifeq 0eb
            // 0c5: bipush 6
            // 0c7: istore 4
            // 0c9: goto 0eb
            // 0cc: aload 3
            // 0cd: ldc_w "hashCode"
            // 0d0: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 0d3: ifeq 0eb
            // 0d6: bipush 7
            // 0d8: istore 4
            // 0da: goto 0eb
            // 0dd: aload 3
            // 0de: ldc_w "equals"
            // 0e1: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
            // 0e4: ifeq 0eb
            // 0e7: bipush 8
            // 0e9: istore 4
            // 0eb: iload 4
            // 0ed: tableswitch 144 0 8 51 60 86 93 97 104 110 116 126
            // 120: getstatic dev/leonetic/util/BaritoneBridge.paused Z
            // 123: invokestatic java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
            // 126: goto 189
            // 129: invokestatic dev/leonetic/util/BaritoneBridge.primaryBaritone ()Ljava/lang/Object;
            // 12c: astore 5
            // 12e: aload 5
            // 130: ldc_w "getInputOverrideHandler"
            // 133: invokestatic dev/leonetic/util/BaritoneBridge.call (Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;
            // 136: ldc_w "clearAllKeys"
            // 139: invokestatic dev/leonetic/util/BaritoneBridge.call (Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;
            // 13c: pop
            // 13d: invokestatic dev/leonetic/util/BaritoneBridge.pauseCommand ()Ljava/lang/Object;
            // 140: goto 189
            // 143: bipush 1
            // 144: invokestatic java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
            // 147: goto 189
            // 14a: aconst_null
            // 14b: goto 189
            // 14e: dconst_0
            // 14f: invokestatic java/lang/Double.valueOf (D)Ljava/lang/Double;
            // 152: goto 189
            // 155: ldc_w "Homovore"
            // 158: goto 189
            // 15b: ldc_w "HomovorePauseProcess"
            // 15e: goto 189
            // 161: aload 0
            // 162: invokestatic java/lang/System.identityHashCode (Ljava/lang/Object;)I
            // 165: invokestatic java/lang/Integer.valueOf (I)Ljava/lang/Integer;
            // 168: goto 189
            // 16b: aload 0
            // 16c: aload 2
            // 16d: bipush 0
            // 16e: aaload
            // 16f: if_acmpne 176
            // 172: bipush 1
            // 173: goto 177
            // 176: bipush 0
            // 177: invokestatic java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
            // 17a: goto 189
            // 17d: new java/lang/UnsupportedOperationException
            // 180: dup
            // 181: aload 1
            // 182: invokevirtual java/lang/reflect/Method.getName ()Ljava/lang/String;
            // 185: invokespecial java/lang/UnsupportedOperationException.<init> (Ljava/lang/String;)V
            // 188: athrow
            // 189: areturn
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
         Object requestPause = Enum.valueOf(commandType, "REQUEST_PAUSE");
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
