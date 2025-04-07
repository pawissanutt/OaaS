package org.hpcclab.oaas.crm.env;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.metrics.v1beta1.NodeMetrics;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.hpcclab.oaas.crm.CrmConfig;
import org.hpcclab.oaas.crm.env.OprcEnvironment.EnvResource;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EnvironmentManager {
  private static final Logger logger = LoggerFactory.getLogger(EnvironmentManager.class);
//  final KubernetesClient client;
  OprcEnvironment environment;
  Map<String, KubernetesClient> clientMap = new HashMap<>();

  @Inject
  public EnvironmentManager(KubernetesClient client,
                            CrmConfig conf) {
//    this.client = client;
    var configProvider = ConfigProvider.getConfig();
    var kafka = configProvider
      .getValue("oprc.envconf.kafka", String.class);
    var pmHost = configProvider
      .getValue("oprc.envconf.pmHost", String.class);
    var pmPort = configProvider
      .getValue("oprc.envconf.pmPort", String.class);
    var envConf = OprcEnvironment.EnvConfig.builder()
      .namespace(conf.namespace())
      .kafkaBootstrap(kafka)
      .classManagerHost(pmHost)
      .classManagerPort(pmPort)
      .exposeKnative(conf.exposeKnative())
      .useKnativeLb(conf.useKnativeLb())
      .logLevel(configProvider.getValue("oprc.log", String.class))
      .clsTopic(conf.clsProvisionTopic())
      .fnTopic(conf.fnProvisionTopic())
      .crHashTopic(conf.crHashTopic())
      .feasibleCheckDisable(conf.feasibleCheckDisable())
      .build();

    var envList = parseConfig(conf.managedEnv(), envConf);
    logger.info("Managing Envs: {}", envList);
    environment = new OprcEnvironment(envList);
  }

  public OprcEnvironment getEnvironment() {
    return environment;
  }

  public OprcEnvironment.EnvConfig getEnvConfig(String envName) {
    return environment.findEnvConfig(envName);
  }

  public KubernetesClient getK8sClient(String envName) {
    if (clientMap.containsKey(envName)) {
      return clientMap.get(envName);
    }
    OprcEnvironment.EnvConfig envConfig = environment.findEnvConfig(envName);
    String kubeContext = envConfig.kubeContext();
    var config = Config.autoConfigure(kubeContext);
    if (envConfig.namespace()!= null) {
      config.setNamespace(envConfig.namespace());
    }
    var client= new KubernetesClientBuilder().withConfig(config).build();
    clientMap.put(envName, client);
    return client;
  }

  public void refresh(String envName) {
    var client = getK8sClient(envName);
    var nodeMetricsList = client.top().nodes()
      .metrics().getItems();
    List<Node> nodes = client.nodes().list().getItems();
    EnvResource total = nodes.stream()
      .map(node -> node.getStatus().getAllocatable())
      .map(m -> new EnvResource(
        m.get("cpu").getNumericalAmount(),
        m.get("memory").getNumericalAmount())
      )
      .reduce(EnvResource.ZERO, EnvResource::sum);
    EnvResource usage = nodeMetricsList.stream()
      .map(NodeMetrics::getUsage)
      .map(EnvResource::new)
      .reduce(EnvResource.ZERO, EnvResource::sum);
    EnvResource requests = calculateRequest(client);
    EnvResource remaining = total.subtract(requests);
    logger.info("current resources: total {}, usage {}, remaining {}",
      total, usage, remaining);
    var conf = environment.findEnvConfig(envName).toBuilder()
      .total(total)
      .usable(remaining)
      .request(requests)
      .build();
    environment.getManagedEnvs().put(conf.name(), conf);
  }

  private EnvResource calculateRequest(KubernetesClient client) {
    PodList podList = client.pods().inAnyNamespace().list();
    // Initialize a map to store total requested resources by node
    double totalCPURequests = 0.0;
    long totalMemoryRequests = 0L;

    // Iterate through each pod and accumulate resource requests by node
    for (Pod pod : podList.getItems()) {
      for (Container container : pod.getSpec().getContainers()) {
        if (container.getResources()==null ||
          container.getResources().getRequests()==null) continue;
        var cpu = container.getResources().getRequests().get("cpu");
        var mem = container.getResources().getRequests().get("memory");
        if (cpu!=null)
          totalCPURequests += cpu.getNumericalAmount().doubleValue();
        if (mem!=null)
          totalMemoryRequests += mem.getNumericalAmount().longValue();
      }
    }
    return new EnvResource(totalCPURequests, totalMemoryRequests);
  }

  public List<OprcEnvironment.EnvConfig> parseConfig(String yaml, OprcEnvironment.EnvConfig template) {
    YAMLMapper yamlMapper = new YAMLMapper();
    try {
      var list = yamlMapper.readValue(yaml, new TypeReference<List<OprcEnvironment.EnvConfig>>(){});
      for (int i = 0; i < list.size(); i++) {
        var config = list.get(i);
        var builder = replaceNull(template, config);
        if (config.crHashTopic() == null) builder.crHashTopic(template.crHashTopic());
        list.set(i, builder.build());
      }
      return list;
    } catch (JsonProcessingException e) {
      throw new StdOaasException(e);
    }
  }

  private static OprcEnvironment.EnvConfig.EnvConfigBuilder replaceNull(OprcEnvironment.EnvConfig template, OprcEnvironment.EnvConfig config) {
    var builder = config.toBuilder();
    if (config.namespace() == null) builder.namespace(template.namespace());
    if (config.kafkaBootstrap() == null) builder.kafkaBootstrap(template.kafkaBootstrap());
    if (config.classManagerHost() == null) builder.classManagerHost(template.classManagerHost());
    if (config.classManagerPort() == null) builder.classManagerPort(template.classManagerPort());
    if (config.logLevel() == null) builder.logLevel(template.logLevel());
    if (config.clsTopic() == null) builder.clsTopic(template.clsTopic());
    if (config.fnTopic() == null) builder.fnTopic(template.fnTopic());
    if (config.availability() == null) builder.availability(new OprcEnvironment.AvailabilityInfo(0.95));
    return builder;
  }
}
