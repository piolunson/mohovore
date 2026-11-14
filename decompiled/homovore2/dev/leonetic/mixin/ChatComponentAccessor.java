package dev.leonetic.mixin;

import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_303;
import net.minecraft.class_338;
import net.minecraft.class_7469;
import net.minecraft.class_7591;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_338.class)
public interface ChatComponentAccessor {
   @Accessor("field_2061")
   List<class_303> homovore$getAllMessages();

   @Invoker("method_44813")
   void homovore$refreshTrimmedMessages();

   @Invoker("method_44811")
   void homovore$addMessage(class_2561 var1, class_7469 var2, class_7591 var3);
}
