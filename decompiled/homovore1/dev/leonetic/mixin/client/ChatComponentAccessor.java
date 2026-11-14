package dev.leonetic.mixin.client;

import java.util.List;
import net.minecraft.class_303;
import net.minecraft.class_338;
import net.minecraft.class_303.class_7590;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(class_338.class)
public interface ChatComponentAccessor {
   @Accessor("field_2061")
   List<class_303> homovore$getAllMessages();

   @Accessor("field_2064")
   List<class_7590> homovore$getTrimmedMessages();

   @Invoker("method_44813")
   void homovore$refreshTrimmedMessages();
}
