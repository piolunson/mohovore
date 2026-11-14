package dev.leonetic.v2.util;

import com.google.gson.JsonElement;

public interface Jsonable {
   JsonElement toJson();

   void fromJson(JsonElement var1);

   default String getFileName() {
      return "";
   }

   default boolean isShareable() {
      return true;
   }
}
