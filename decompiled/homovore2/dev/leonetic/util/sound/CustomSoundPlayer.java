package dev.leonetic.util.sound;

import dev.leonetic.Homovore2;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat.Encoding;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1102;
import net.minecraft.class_1111;
import net.minecraft.class_1113;
import net.minecraft.class_156;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3419;
import net.minecraft.class_4228;
import net.minecraft.class_4231;
import net.minecraft.class_4235;
import net.minecraft.class_5819;
import net.minecraft.class_1113.class_1114;
import net.minecraft.class_4225.class_4105;
import org.lwjgl.BufferUtils;

public final class CustomSoundPlayer {
   public static final String DEFAULT = "Default";
   public static final String NONE = "None";
   private static final int NORMALIZED_PEAK = 29204;
   private static final Path FOLDER = FabricLoader.getInstance().getGameDir().resolve("homovore/sounds");
   private static final List<String> BUNDLED = List.of("click deleera.wav", "scroll.wav");
   private static boolean bundledInstalled;
   private static final Map<Path, CustomSoundPlayer.CachedBuffer> CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentLinkedQueue<class_4231> STALE_BUFFERS = new ConcurrentLinkedQueue<>();
   private static volatile List<String> names = List.of("Default", "None");

   private CustomSoundPlayer() {
   }

   public static synchronized void rescanFolder() {
      try {
         Files.createDirectories(FOLDER);
         installBundled();

         List<Path> files;
         try (Stream<Path> stream = Files.list(FOLDER)) {
            files = stream.filter(x$0 -> Files.isRegularFile(x$0))
               .filter(CustomSoundPlayer::isSupported)
               .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
               .toList();
         }

         List<String> found = new ArrayList<>(files.size() + 2);
         found.add("Default");
         found.add("None");

         for (Path file : files) {
            found.add(file.getFileName().toString());
         }

         names = List.copyOf(found);
         Map<Path, CustomSoundPlayer.FileStamp> current = new ConcurrentHashMap<>();

         for (Path file : files) {
            current.put(file, stamp(file));
         }

         CACHE.entrySet().removeIf(entry -> {
            CustomSoundPlayer.FileStamp now = current.get(entry.getKey());
            if (Objects.equals(now, entry.getValue().stamp())) {
               return false;
            }

            class_4231 buffer = entry.getValue().future().getNow(null);
            if (buffer != null) {
               STALE_BUFFERS.add(buffer);
            }

            return true;
         });
      } catch (IOException exception) {
         Homovore2.LOGGER.warn("Could not scan custom sound directory {}: {}", FOLDER, exception.toString());
      }
   }

   private static void installBundled() {
      if (!bundledInstalled) {
         bundledInstalled = true;

         for (String name : BUNDLED) {
            Path target = FOLDER.resolve(name);
            if (!Files.exists(target)) {
               try (InputStream stream = CustomSoundPlayer.class.getResourceAsStream("/homovore-2/sounds/" + name)) {
                  if (stream == null) {
                     Homovore2.LOGGER.warn("Bundled sound {} is missing from the jar", name);
                  } else {
                     Files.copy(stream, target);
                  }
               } catch (IOException exception) {
                  Homovore2.LOGGER.warn("Could not install bundled sound {}: {}", name, exception.toString());
               }
            }
         }
      }
   }

   public static List<String> names() {
      return names;
   }

   public static boolean isDefault(String selection) {
      return selection == null || "Default".equalsIgnoreCase(selection);
   }

   public static boolean isNone(String selection) {
      return "None".equalsIgnoreCase(selection);
   }

   public static boolean isCustom(String selection) {
      return !isDefault(selection) && !isNone(selection) && resolve(selection) != null;
   }

   public static void play(String selection, class_3419 source) {
      Path file = resolve(selection);
      if (file != null) {
         class_310 minecraft = class_310.method_1551();
         if (minecraft.method_1483() != null) {
            minecraft.method_1483().method_4873(new CustomSoundPlayer.Instance(file, source));
         }
      }
   }

   public static void play(String selection, class_1113 original) {
      Path file = resolve(selection);
      if (file != null) {
         class_310 minecraft = class_310.method_1551();
         if (minecraft.method_1483() != null) {
            original.method_4783(minecraft.method_1483());
            class_1111 resolved = original.method_4776();
            int baseDistance = resolved == null ? 16 : resolved.method_4770();
            float volume = original.method_4781();
            float pitch = original.method_4782();
            float attenuationDistance = Math.max(volume, 1.0F) * baseDistance;
            minecraft.method_1483().method_4873(new CustomSoundPlayer.Instance(file, original, volume, pitch, attenuationDistance));
         }
      }
   }

   public static void play(class_4235 channels, CustomSoundPlayer.Instance instance) {
      float volume = Math.max(0.0F, Math.min(1.0F, instance.method_4781())) * class_310.method_1551().field_1690.method_71978(instance.method_4774());
      if (!(volume <= 0.0F)) {
         load(instance.file()).whenComplete((buffer, failure) -> {
            if (failure != null) {
               Homovore2.LOGGER.warn("Could not play custom sound {}: {}", instance.file(), rootMessage(failure));
            } else {
               channels.method_19723(class_4105.field_18352).thenAccept(handle -> {
                  if (handle != null) {
                     handle.method_19735(channel -> {
                        discardStaleBuffers();
                        channel.method_19641(new class_243(instance.method_4784(), instance.method_4779(), instance.method_4778()));
                        channel.method_19649(instance.method_4787());
                        if (instance.method_4777() == class_1114.field_5476) {
                           channel.method_19651(instance.attenuationDistance());
                        } else {
                           channel.method_19657();
                        }

                        channel.method_19639(Math.max(0.5F, Math.min(2.0F, instance.method_4782())));
                        channel.method_19647(Math.min(1.0F, volume));
                        channel.method_19642(buffer);
                        channel.method_19650();
                     });
                  }
               });
            }
         });
      }
   }

   public static void clear(class_4235 channels) {
      for (CustomSoundPlayer.CachedBuffer cached : CACHE.values()) {
         class_4231 buffer = cached.future().getNow(null);
         if (buffer != null) {
            STALE_BUFFERS.add(buffer);
         }
      }

      CACHE.clear();
      channels.method_19727(ignored -> discardStaleBuffers());
   }

   private static CompletableFuture<class_4231> load(Path file) {
      CustomSoundPlayer.FileStamp stamp;
      try {
         stamp = stamp(file);
      } catch (IOException exception) {
         return CompletableFuture.failedFuture(exception);
      }

      CustomSoundPlayer.CachedBuffer cached = CACHE.compute(file, (path, old) -> {
         if (old != null && old.stamp().equals(stamp)) {
            return (CustomSoundPlayer.CachedBuffer)old;
         }

         if (old != null) {
            class_4231 buffer = old.future().getNow(null);
            if (buffer != null) {
               STALE_BUFFERS.add(buffer);
            }
         }

         CompletableFuture<class_4231> future = CompletableFuture.supplyAsync(() -> {
            try {
               return decode(path);
            } catch (Exception exception) {
               throw new RuntimeException(exception);
            }
         }, class_156.method_55473());
         return new CustomSoundPlayer.CachedBuffer(stamp, future);
      });
      return cached.future();
   }

   private static class_4231 decode(Path file) throws Exception {
      String extension = extension(file);

      return switch (extension) {
         case "ogg" -> decodeOgg(file);
         case "wav" -> decodeWav(file);
         case "mp3" -> decodeMp3(file);
         default -> throw new IOException("Unsupported file type: " + extension);
      };
   }

   private static class_4231 decodeOgg(Path file) throws IOException {
      class_4231 var4;
      try (InputStream input = Files.newInputStream(file)) {
         class_4228 stream = new class_4228(input);

         try {
            AudioFormat format = stream.method_19719();
            var4 = new class_4231(normalize(stream.method_59757(), format), format);
         } catch (Throwable var7) {
            try {
               stream.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }

            throw var7;
         }

         stream.close();
      }

      return var4;
   }

   private static class_4231 decodeWav(Path file) throws Exception {
      try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
         AudioFormat original = source.getFormat();
         int channels = original.getChannels();
         if (channels < 1 || channels > 2) {
            throw new IOException("WAV must be mono or stereo");
         }

         AudioFormat pcm = new AudioFormat(Encoding.PCM_SIGNED, original.getSampleRate(), 16, channels, channels * 2, original.getSampleRate(), false);

         try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, source)) {
            return buffer(converted.readAllBytes(), pcm);
         }
      }
   }

   private static class_4231 decodeMp3(Path file) throws Exception {
      try (
         InputStream input = Files.newInputStream(file);
         ByteArrayOutputStream pcm = new ByteArrayOutputStream();
      ) {
         Bitstream bitstream = new Bitstream(input);

         try {
            Decoder decoder = new Decoder();
            int sampleRate = -1;
            int channels = -1;

            Header header;
            while ((header = bitstream.readFrame()) != null) {
               try {
                  SampleBuffer samples = (SampleBuffer)decoder.decodeFrame(header, bitstream);
                  if (sampleRate < 0) {
                     sampleRate = samples.getSampleFrequency();
                     channels = samples.getChannelCount();
                  }

                  short[] values = samples.getBuffer();

                  for (int index = 0; index < samples.getBufferLength(); index++) {
                     short value = values[index];
                     pcm.write(value & 255);
                     pcm.write(value >>> 8 & 0xFF);
                  }
               } finally {
                  bitstream.closeFrame();
               }
            }

            if (sampleRate <= 0 || channels < 1 || channels > 2) {
               throw new IOException("MP3 contains no supported mono or stereo audio");
            }

            AudioFormat format = new AudioFormat(Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false);
            return buffer(pcm.toByteArray(), format);
         } finally {
            bitstream.close();
         }
      }
   }

   private static class_4231 buffer(byte[] bytes, AudioFormat format) {
      ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
      data.put(bytes).flip();
      return new class_4231(normalize(data, format), format);
   }

   private static ByteBuffer normalize(ByteBuffer data, AudioFormat format) {
      if (Encoding.PCM_SIGNED.equals(format.getEncoding()) && format.getSampleSizeInBits() == 16 && data.remaining() >= 2) {
         ByteBuffer samples = data.duplicate().order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
         int start = samples.position();
         int end = samples.limit() - 1;
         int peak = 0;

         for (int index = start; index < end; index += 2) {
            short sample = samples.getShort(index);
            int magnitude = sample == -32768 ? 32768 : Math.abs(sample);
            peak = Math.max(peak, magnitude);
         }

         if (peak <= 29204) {
            return data;
         }

         float gain = 29204.0F / peak;

         for (int index = start; index < end; index += 2) {
            int scaled = Math.round(samples.getShort(index) * gain);
            samples.putShort(index, (short)Math.max(-32768, Math.min(32767, scaled)));
         }

         return data;
      } else {
         return data;
      }
   }

   private static Path resolve(String selection) {
      if (selection != null && !selection.isBlank() && !isDefault(selection) && !isNone(selection)) {
         Path file = FOLDER.resolve(selection).normalize();
         return file.getParent() != null && file.getParent().equals(FOLDER) && Files.isRegularFile(file) && isSupported(file) ? file : null;
      } else {
         return null;
      }
   }

   private static boolean isSupported(Path file) {
      return switch (extension(file)) {
         case "ogg", "mp3", "wav" -> true;
         default -> false;
      };
   }

   private static String extension(Path file) {
      String name = file.getFileName().toString();
      int dot = name.lastIndexOf(46);
      return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
   }

   private static CustomSoundPlayer.FileStamp stamp(Path file) throws IOException {
      return new CustomSoundPlayer.FileStamp(Files.size(file), Files.getLastModifiedTime(file).toMillis());
   }

   private static void discardStaleBuffers() {
      class_4231 buffer;
      while ((buffer = STALE_BUFFERS.poll()) != null) {
         buffer.method_19687();
      }
   }

   private static String rootMessage(Throwable failure) {
      Throwable cause = failure;

      while (cause.getCause() != null) {
         cause = cause.getCause();
      }

      return cause.toString();
   }

   private record CachedBuffer(CustomSoundPlayer.FileStamp stamp, CompletableFuture<class_4231> future) {
   }

   private record FileStamp(long size, long modified) {
   }

   public static final class Instance extends class_1102 {
      private final Path file;
      private final float attenuationDistance;

      private Instance(Path file, class_3419 source) {
         super(Homovore2.id("external_sound"), source, class_5819.method_43047());
         this.file = file;
         this.attenuationDistance = 16.0F;
         this.field_18936 = true;
         this.field_5440 = class_1114.field_5478;
      }

      private Instance(Path file, class_1113 original, float volume, float pitch, float attenuationDistance) {
         super(Homovore2.id("external_sound"), original.method_4774(), class_5819.method_43047());
         this.file = file;
         this.attenuationDistance = attenuationDistance;
         this.field_5442 = volume;
         this.field_5441 = pitch;
         this.field_5439 = original.method_4784();
         this.field_5450 = original.method_4779();
         this.field_5449 = original.method_4778();
         this.field_18936 = original.method_4787();
         this.field_5440 = original.method_4777();
      }

      public Path file() {
         return this.file;
      }

      public float attenuationDistance() {
         return this.attenuationDistance;
      }

      public float method_4781() {
         return this.field_5442;
      }

      public float method_4782() {
         return this.field_5441;
      }
   }
}
