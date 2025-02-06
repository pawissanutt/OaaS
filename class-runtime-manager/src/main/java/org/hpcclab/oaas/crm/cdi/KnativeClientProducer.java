package org.hpcclab.oaas.crm.cdi;

import io.fabric8.knative.client.DefaultKnativeClient;
import io.fabric8.knative.client.KnativeClient;
import io.fabric8.kubernetes.client.Config;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.hpcclab.oaas.crm.CrmConfig;

@Singleton
public class KnativeClientProducer {

  @Produces
  public KnativeClient knativeClient(Config config) {
    return new DefaultKnativeClient(config);
  }

  @Produces
  public Config config(CrmConfig crmConfig) {
    return Config.autoConfigure(crmConfig.kubeContext().orElse(null));
  }
}
