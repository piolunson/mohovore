package dev.piolunson.v2.util.network;

import dev.piolunson.v2.Homovore2;
import java.io.IOException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.network.Address;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.ServerAddress;

public final class EndpointOptimizer {
   private static final int MAX_ENDPOINTS = 16;
   private static final AtomicReference<EndpointOptimizer.Config> CONFIG = new AtomicReference<>(EndpointOptimizer.Config.disabled());
   private static final AtomicReference<EndpointOptimizer.CachedChoice> CACHE = new AtomicReference<>();

   private EndpointOptimizer() {
   }

   public static void configure(EndpointOptimizer.Config config) {
      EndpointOptimizer.Config next = config == null ? EndpointOptimizer.Config.disabled() : config;
      EndpointOptimizer.Config previous = CONFIG.getAndSet(next);
      if (!previous.equals(next)) {
         CACHE.set(null);
      }
   }

   public static Optional<Address> resolve(AllowedAddressResolver resolver, ServerAddress requested) {
      Optional<Address> vanilla = resolver.resolve(requested);
      EndpointOptimizer.Config config = CONFIG.get();
      if (config.enabled() && !vanilla.isEmpty() && matches(requested.getAddress(), config.host())) {
         Address fallback = vanilla.get();

         try {
            List<InetAddress> candidates = candidates(fallback, config.addressFamily());
            if (candidates.isEmpty()) {
               return vanilla;
            } else if (candidates.size() == 1) {
               InetAddress only = candidates.getFirst();
               return Optional.of(resolved(fallback.getHostName(), only, fallback.getPort()));
            } else {
               String fingerprint = fingerprint(candidates);
               long now = System.nanoTime();
               EndpointOptimizer.CachedChoice cached = CACHE.get();
               if (cached != null
                  && cached.port() == fallback.getPort()
                  && cached.host().equals(normalize(requested.getAddress()))
                  && cached.fingerprint().equals(fingerprint)
                  && now - cached.measuredAtNanos() < config.cacheNanos()) {
                  return Optional.of(resolved(fallback.getHostName(), cached.address(), fallback.getPort()));
               } else {
                  List<EndpointOptimizer.Measurement> measurements = measure(candidates, fallback.getPort(), config);
                  EndpointOptimizer.Measurement best = choose(measurements, cached, config.switchThresholdPercent());
                  if (best == null) {
                     Homovore2.LOGGER
                        .warn(
                           "2b2t optimizer: every one of {} DNS endpoints failed its TCP probes; using vanilla resolution {}",
                           candidates.size(),
                           fallback.getHostAddress()
                        );
                     return vanilla;
                  } else {
                     EndpointOptimizer.CachedChoice choice = new EndpointOptimizer.CachedChoice(
                        normalize(requested.getAddress()), fallback.getPort(), fingerprint, best.address(), now
                     );
                     CACHE.set(choice);
                     Homovore2.LOGGER
                        .info(
                           "2b2t optimizer: selected {} from {} DNS endpoints (median {} ms, jitter {} ms, {}/{} probes)",
                           new Object[]{
                              best.address().getHostAddress(),
                              candidates.size(),
                              millis(best.medianNanos()),
                              millis(best.jitterNanos()),
                              best.successes(),
                              config.samples()
                           }
                        );
                     return Optional.of(resolved(fallback.getHostName(), best.address(), fallback.getPort()));
                  }
               }
            }
         } catch (UnknownHostException exception) {
            Homovore2.LOGGER.warn("2b2t optimizer: could not enumerate DNS endpoints; using vanilla resolution", exception);
            return vanilla;
         } catch (RuntimeException exception) {
            Homovore2.LOGGER.warn("2b2t optimizer: endpoint measurement failed; using vanilla resolution", exception);
            return vanilla;
         }
      } else {
         return vanilla;
      }
   }

   private static List<InetAddress> candidates(Address fallback, EndpointOptimizer.AddressFamily family) throws UnknownHostException {
      List<InetAddress> found = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      addCandidate(found, seen, fallback.getInetSocketAddress().getAddress(), family);

      for (InetAddress address : InetAddress.getAllByName(fallback.getHostName())) {
         addCandidate(found, seen, address, family);
         if (found.size() >= 16) {
            break;
         }
      }

      found.sort(Comparator.comparing(InetAddress::getHostAddress));
      return List.copyOf(found);
   }

   private static void addCandidate(List<InetAddress> found, Set<String> seen, InetAddress address, EndpointOptimizer.AddressFamily family) {
      if (address != null && family.accepts(address)) {
         String key = address.getHostAddress();
         if (seen.add(key)) {
            found.add(address);
         }
      }
   }

   private static List<EndpointOptimizer.Measurement> measure(List<InetAddress> candidates, int port, EndpointOptimizer.Config config) {
      List<Future<EndpointOptimizer.Measurement>> futures = new ArrayList<>(candidates.size());

      Object var16;
      try (ExecutorService probes = Executors.newVirtualThreadPerTaskExecutor()) {
         for (InetAddress candidate : candidates) {
            futures.add(probes.submit(() -> measure(candidate, port, config)));
         }

         List<EndpointOptimizer.Measurement> results = new ArrayList<>(futures.size());

         for (Future<EndpointOptimizer.Measurement> future : futures) {
            try {
               results.add(future.get());
            } catch (InterruptedException exception) {
               Thread.currentThread().interrupt();
               return List.of();
            } catch (ExecutionException exception) {
               Homovore2.LOGGER.debug("Endpoint probe worker failed", exception.getCause());
            }
         }

         var16 = results;
      }

      return (List<EndpointOptimizer.Measurement>)var16;
   }

   private static EndpointOptimizer.Measurement measure(InetAddress address, int port, EndpointOptimizer.Config config) {
      long[] successful = new long[config.samples()];
      int successes = 0;

      for (int sample = 0; sample < config.samples(); sample++) {
         long started = System.nanoTime();

         try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(address, port), config.timeoutMillis());
            successful[successes++] = System.nanoTime() - started;
         } catch (IOException var17) {
         }
      }

      if (successes == 0) {
         return new EndpointOptimizer.Measurement(address, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0);
      }

      Arrays.sort(successful, 0, successes);
      long median = successful[(successes - 1) / 2];
      long[] deviations = new long[successes];

      for (int i = 0; i < successes; i++) {
         deviations[i] = Math.abs(successful[i] - median);
      }

      Arrays.sort(deviations);
      long jitter = deviations[(successes - 1) / 2];
      int failures = config.samples() - successes;
      long failurePenalty = TimeUnit.MILLISECONDS.toNanos(config.timeoutMillis()) * failures;
      long score = saturatingAdd(median, Math.round(jitter * config.jitterWeight()));
      score = saturatingAdd(score, failurePenalty);
      return new EndpointOptimizer.Measurement(address, median, jitter, score, successes);
   }

   private static EndpointOptimizer.Measurement choose(
      List<EndpointOptimizer.Measurement> results, EndpointOptimizer.CachedChoice previous, int switchThresholdPercent
   ) {
      EndpointOptimizer.Measurement best = results.stream()
         .filter(EndpointOptimizer.Measurement::reachable)
         .min(Comparator.comparingLong(EndpointOptimizer.Measurement::scoreNanos).thenComparing(result -> result.address().getHostAddress()))
         .orElse(null);
      if (best != null && previous != null) {
         EndpointOptimizer.Measurement incumbent = results.stream()
            .filter(result -> result.address().equals(previous.address()) && result.reachable())
            .findFirst()
            .orElse(null);
         if (incumbent == null) {
            return best;
         }

         double requiredImprovement = 1.0 - switchThresholdPercent / 100.0;
         return best.scoreNanos() < incumbent.scoreNanos() * requiredImprovement ? best : incumbent;
      } else {
         return best;
      }
   }

   private static Address resolved(String handshakeHost, InetAddress address, int port) throws UnknownHostException {
      InetAddress named = InetAddress.getByAddress(handshakeHost, address.getAddress());
      return Address.create(new InetSocketAddress(named, port));
   }

   private static String fingerprint(List<InetAddress> addresses) {
      return addresses.stream().map(InetAddress::getHostAddress).sorted().reduce((left, right) -> left + "," + right).orElse("");
   }

   private static boolean matches(String requestedHost, String configuredHost) {
      return normalize(requestedHost).equals(normalize(configuredHost));
   }

   private static String normalize(String host) {
      if (host == null) {
         return "";
      }

      String trimmed = host.trim();

      while (trimmed.endsWith(".")) {
         trimmed = trimmed.substring(0, trimmed.length() - 1);
      }

      if (trimmed.isEmpty()) {
         return "";
      }

      try {
         return IDN.toASCII(trimmed).toLowerCase(Locale.ROOT);
      } catch (IllegalArgumentException ignored) {
         return trimmed.toLowerCase(Locale.ROOT);
      }
   }

   private static long saturatingAdd(long left, long right) {
      return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
   }

   private static long millis(long nanos) {
      return TimeUnit.NANOSECONDS.toMillis(nanos);
   }

   public enum AddressFamily {
      AUTO,
      IPV4_ONLY,
      IPV6_ONLY;

      private boolean accepts(InetAddress address) {
         return switch (this) {
            case AUTO -> true;
            case IPV4_ONLY -> address instanceof Inet4Address;
            case IPV6_ONLY -> address instanceof Inet6Address;
         };
      }
   }

   private record CachedChoice(String host, int port, String fingerprint, InetAddress address, long measuredAtNanos) {
   }

   public record Config(
      boolean enabled,
      String host,
      int samples,
      int timeoutMillis,
      long cacheNanos,
      double jitterWeight,
      int switchThresholdPercent,
      EndpointOptimizer.AddressFamily addressFamily
   ) {
      public Config {
         host = host == null ? "" : host;
         samples = Math.clamp(samples, 1, 8);
         timeoutMillis = Math.clamp(timeoutMillis, 100, 5000);
         cacheNanos = Math.max(0L, cacheNanos);
         jitterWeight = Math.clamp(jitterWeight, 0.0, 10.0);
         switchThresholdPercent = Math.clamp(switchThresholdPercent, 0, 50);
         addressFamily = addressFamily == null ? EndpointOptimizer.AddressFamily.AUTO : addressFamily;
      }

      public static EndpointOptimizer.Config disabled() {
         return new EndpointOptimizer.Config(false, "2b2t.org", 3, 800, 0L, 1.5, 15, EndpointOptimizer.AddressFamily.AUTO);
      }
   }

   private record Measurement(InetAddress address, long medianNanos, long jitterNanos, long scoreNanos, int successes) {
      private boolean reachable() {
         return this.successes > 0;
      }
   }
}
