package dev.piolunson.v2.feature.command;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

public final class CommandExceptions {
   private CommandExceptions() {
   }

   public static SimpleCommandExceptionType invalidArgument(String message, Object... format) {
      return new SimpleCommandExceptionType(new LiteralMessage(String.format(message, format)));
   }
}
