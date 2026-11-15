package dev.piolunson.v2.feature.module.impl;

import dev.piolunson.v2.feature.module.Category;
import dev.piolunson.v2.feature.module.Module;
import dev.piolunson.v2.feature.setting.Setting;
import dev.piolunson.v2.util.network.EndpointOptimizer;
import java.util.concurrent.TimeUnit;

public final class Server extends Module {
   public final Setting<String> host = this.string("Host", "2b2t.org")
      .describe("Exact hostname to optimize. Addresses are taken only from its live DNS answer.");
   public final Setting<EndpointOptimizer.AddressFamily> addressFamily = this.mode("AddressFamily", EndpointOptimizer.AddressFamily.AUTO)
      .describe("Benchmark every supported address, or restrict selection to IPv4/IPv6.");
   public final Setting<Integer> samples = this.integer("Samples", 3, 1, 8)
      .describe("TCP connection samples per DNS endpoint. More samples are steadier but slower.")
      .page("Measurement");
   public final Setting<Integer> timeout = this.integer("Timeout", 800, 100, 5000)
      .describe("Milliseconds allowed for each TCP connection probe.")
      .page("Measurement");
   public final Setting<Double> jitterWeight = this.number("JitterWeight", (double)1.5, (double)0.0, (double)10.0)
      .describe("How strongly unstable latency is penalized relative to median latency.")
      .page("Scoring");
   public final Setting<Integer> switchThreshold = this.integer("SwitchThreshold", 15, 0, 50)
      .describe("Percent faster a challenger must be before replacing the previous endpoint.")
      .page("Scoring");
   public final Setting<Integer> cacheMinutes = this.integer("CacheMinutes", 15, 1, 120)
      .describe("Reuse a winner while DNS is unchanged; expired results are remeasured.")
      .page("Scoring");

   public Server() {
      super("Server", Category.CLIENT, "Benchmarks a server's DNS endpoints and connects through the quickest stable one.");
   }

   @Override
   public void onEnable() {
      this.publish();
   }

   @Override
   public void onDisable() {
      EndpointOptimizer.configure(EndpointOptimizer.Config.disabled());
   }

   @Override
   public void onTick() {
      this.publish();
   }

   private void publish() {
      EndpointOptimizer.configure(
         new EndpointOptimizer.Config(
            this.isEnabled(),
            this.host.getValue(),
            this.samples.getValue(),
            this.timeout.getValue(),
            TimeUnit.MINUTES.toNanos(this.cacheMinutes.getValue().intValue()),
            this.jitterWeight.getValue(),
            this.switchThreshold.getValue(),
            this.addressFamily.getValue()
         )
      );
   }

   @Override
   public boolean isPersonal() {
      return true;
   }
}
