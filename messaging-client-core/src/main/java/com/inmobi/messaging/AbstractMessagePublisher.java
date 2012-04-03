package com.inmobi.messaging;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inmobi.instrumentation.TimingAccumulator;
import com.inmobi.stats.EmitterRegistry;
import com.inmobi.stats.StatsEmitter;
import com.inmobi.stats.StatsExposer;

public abstract class AbstractMessagePublisher implements MessagePublisher {

  private static final Logger LOG = LoggerFactory
      .getLogger(AbstractMessagePublisher.class);
  private final TimingAccumulator stats = new TimingAccumulator();
  private StatsEmitter emitter;
  private boolean statEnabled = false;
  private StatsExposer statExposer;
  private static final String HEADER_TOPIC = "topic";

  @Override
  public void publish(Message m) {
    getStats().accumulateInvocation();
    // TODO: generate headers
    Map<String, String> headers = new HashMap<String, String>();
    headers.put(HEADER_TOPIC, m.getTopic());
    publish(headers, m);
  }

  protected abstract void publish(Map<String, String> headers, Message m);

  public TimingAccumulator getStats() {
    return stats;
  }

  protected boolean statEmissionEnabled() {
    return statEnabled;
  }

  @Override
  public void init(ClientConfig config) {
    try {
      String emitterConfig = config
          .getString(ClientConfig.EMITTER_CONF_FILE_KEY);
      if (emitterConfig == null) {
        LOG.warn("Stat emitter is disabled as config "
            + ClientConfig.EMITTER_CONF_FILE_KEY + " is not set in the config.");
        return;
      }
      emitter = EmitterRegistry.lookup(emitterConfig);
      final Map<String, String> contexts = new HashMap<String, String>();
      contexts.put("messaging_type", "application");
      statExposer = new StatsExposer() {

        @Override
        public Map<String, Number> getStats() {
          HashMap<String, Number> hash = new HashMap<String, Number>();
          hash.put("cumulativeNanoseconds", stats.getCumulativeNanoseconds());
          hash.put("invocationCount", stats.getInvocationCount());
          hash.put("successCount", stats.getSuccessCount());
          hash.put("unhandledExceptionCount",
              stats.getUnhandledExceptionCount());
          hash.put("gracefulTerminates", stats.getGracefulTerminates());
          hash.put("inFlight", stats.getInFlight());
          return hash;
        }

        @Override
        public Map<String, String> getContexts() {
          return contexts;
        }
      };
      emitter.add(statExposer);
      statEnabled = true;
    } catch (Exception e) {
      LOG.warn("Couldn't find or initialize the configured stats emitter", e);
    }
  }

  @Override
  public void close() {
    if (emitter != null) {
      emitter.remove(statExposer);
    }
  }
}
