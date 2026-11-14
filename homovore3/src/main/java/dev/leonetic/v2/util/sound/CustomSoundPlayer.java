package dev.leonetic.v2.util.sound;

import dev.leonetic.v2.Homovore2;
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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.OggAudioStream;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.client.sound.SoundEngine.RunMode;
import net.minecraft.client.sound.SoundInstance.AttenuationType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.lwjgl.BufferUtils;

public final class CustomSoundPlayer {
   public static final String DEFAULT = "Default";
   public static final String NONE = "None";
   private static final int NORMALIZED_PEAK = 29204;
   private static final Path FOLDER = FabricLoader.getInstance().getGameDir().resolve("homovore/sounds");
   private static final List<String> BUNDLED = List.of("click deleera.wav", "scroll.wav");
   private static boolean bundledInstalled;
   private static final Map<Path, CustomSoundPlayer.CachedBuffer> CACHE = new ConcurrentHashMap<>();
   private static final ConcurrentLinkedQueue<StaticSound> STALE_BUFFERS = new ConcurrentLinkedQueue<>();
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

            StaticSound buffer = entry.getValue().future().getNow(null);
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

   public static void play(String selection, SoundCategory source) {
      Path file = resolve(selection);
      if (file != null) {
         MinecraftClient minecraft = MinecraftClient.getInstance();
         if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(new CustomSoundPlayer.Instance(file, source));
         }
      }
   }

   public static void play(String selection, SoundInstance original) {
      Path file = resolve(selection);
      if (file != null) {
         MinecraftClient minecraft = MinecraftClient.getInstance();
         if (minecraft.getSoundManager() != null) {
            original.getSoundSet(minecraft.getSoundManager());
            Sound resolved = original.getSound();
            int baseDistance = resolved == null ? 16 : resolved.getAttenuation();
            float volume = original.getVolume();
            float pitch = original.getPitch();
            float attenuationDistance = Math.max(volume, 1.0F) * baseDistance;
            minecraft.getSoundManager().play(new CustomSoundPlayer.Instance(file, original, volume, pitch, attenuationDistance));
         }
      }
   }

   public static void play(Channel channels, CustomSoundPlayer.Instance instance) {
      float volume = Math.max(0.0F, Math.min(1.0F, instance.getVolume())) * MinecraftClient.getInstance().options.getSoundVolume(instance.getCategory());
      if (!(volume <= 0.0F)) {
         load(instance.file()).whenComplete((buffer, failure) -> {
            if (failure != null) {
               Homovore2.LOGGER.warn("Could not play custom sound {}: {}", instance.file(), rootMessage(failure));
            } else {
               channels.createSource(RunMode.STATIC).thenAccept(handle -> {
                  if (handle != null) {
                     handle.run(channel -> {
                        discardStaleBuffers();
                        channel.setPosition(new Vec3d(instance.getX(), instance.getY(), instance.getZ()));
                        channel.setRelative(instance.isRelative());
                        if (instance.getAttenuationType() == AttenuationType.LINEAR) {
                           channel.setAttenuation(instance.attenuationDistance());
                        } else {
                           channel.disableAttenuation();
                        }

                        channel.setPitch(Math.max(0.5F, Math.min(2.0F, instance.getPitch())));
                        channel.setVolume(Math.min(1.0F, volume));
                        channel.setBuffer(buffer);
                        channel.play();
                     });
                  }
               });
            }
         });
      }
   }

   public static void clear(Channel channels) {
      for (CustomSoundPlayer.CachedBuffer cached : CACHE.values()) {
         StaticSound buffer = cached.future().getNow(null);
         if (buffer != null) {
            STALE_BUFFERS.add(buffer);
         }
      }

      CACHE.clear();
      channels.execute(ignored -> discardStaleBuffers());
   }

   private static CompletableFuture<StaticSound> load(Path file) {
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
            StaticSound buffer = old.future().getNow(null);
            if (buffer != null) {
               STALE_BUFFERS.add(buffer);
            }
         }

         CompletableFuture<StaticSound> future = CompletableFuture.supplyAsync(() -> {
            try {
               return decode(path);
            } catch (Exception exception) {
               throw new RuntimeException(exception);
            }
         }, Util.getDownloadWorkerExecutor());
         return new CustomSoundPlayer.CachedBuffer(stamp, future);
      });
      return cached.future();
   }

   private static StaticSound decode(Path file) throws Exception {
      String extension = extension(file);

      return switch (extension) {
         case "ogg" -> decodeOgg(file);
         case "wav" -> decodeWav(file);
         case "mp3" -> decodeMp3(file);
         default -> throw new IOException("Unsupported file type: " + extension);
      };
   }

   private static StaticSound decodeOgg(Path file) throws IOException {
      StaticSound var4;
      try (InputStream input = Files.newInputStream(file)) {
         OggAudioStream stream = new OggAudioStream(input);

         try {
            AudioFormat format = stream.getFormat();
            var4 = new StaticSound(normalize(stream.readAll(), format), format);
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

   private static StaticSound decodeWav(Path file) throws Exception {
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

   private static StaticSound decodeMp3(Path file) throws Exception {
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

   private static StaticSound buffer(byte[] bytes, AudioFormat format) {
      ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
      data.put(bytes).flip();
      return new StaticSound(normalize(data, format), format);
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
      StaticSound buffer;
      while ((buffer = STALE_BUFFERS.poll()) != null) {
         buffer.close();
      }
   }

   private static String rootMessage(Throwable failure) {
      Throwable cause = failure;

      while (cause.getCause() != null) {
         cause = cause.getCause();
      }

      return cause.toString();
   }

   private record CachedBuffer(CustomSoundPlayer.FileStamp stamp, CompletableFuture<StaticSound> future) {
   }

   private record FileStamp(long size, long modified) {
   }

   public static final class Instance extends AbstractSoundInstance {
      private final Path file;
      private final float attenuationDistance;

      private Instance(Path file, SoundCategory source) {
         super(Homovore2.id("external_sound"), source, Random.create());
         this.file = file;
         this.attenuationDistance = 16.0F;
         this.relative = true;
         this.attenuationType = AttenuationType.NONE;
      }

      private Instance(Path file, SoundInstance original, float volume, float pitch, float attenuationDistance) {
         super(Homovore2.id("external_sound"), original.getCategory(), Random.create());
         this.file = file;
         this.attenuationDistance = attenuationDistance;
         this.volume = volume;
         this.pitch = pitch;
         this.x = original.getX();
         this.y = original.getY();
         this.z = original.getZ();
         this.relative = original.isRelative();
         this.attenuationType = original.getAttenuationType();
      }

      public Path file() {
         return this.file;
      }

      public float attenuationDistance() {
         return this.attenuationDistance;
      }

      public float getVolume() {
         return this.volume;
      }

      public float getPitch() {
         return this.pitch;
      }
   }
}
