package org.hpcclab.oaas.crm.controller;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.hpcclab.oaas.crm.CrtMappingConfig;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.hpcclab.oaas.crm.filter.ConfigmapInjectingFilter;
import org.hpcclab.oaas.crm.filter.SecretInjectingFilter;
import org.hpcclab.oaas.crm.optimize.CrAdjustmentPlan;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;
import org.hpcclab.oaas.repository.store.DatastoreConfRegistry;

import java.util.List;
import java.util.Map;

import static org.hpcclab.oaas.crm.controller.K8SCrController.*;

/**
 * @author Pawissanutt
 */
public class ConfigK8sCrComponentController extends AbstractK8sCrComponentController {
  public ConfigK8sCrComponentController(CrtMappingConfig.CrComponentConfig svcConfig,
                                        OprcEnvironment.Config envConfig) {
    super(svcConfig, envConfig);
  }

  @Override
  public void init(CrController parentController) {
    super.init(parentController);
    if (parentController instanceof K8SCrController kController) {
      SecretInjectingFilter secFilter = new SecretInjectingFilter(prefix + NAME_SECRET);
      ConfigmapInjectingFilter cmFilter = new ConfigmapInjectingFilter(prefix + NAME_CONFIGMAP);
      for (var componentController : kController.componentControllers.values()) {
        componentController.addFilter(secFilter);
        componentController.addFilter(cmFilter);
      }
    }
  }

  @Override
  public List<HasMetadata> doCreateDeployOperation(CrDeploymentPlan plan) {
    var labels = Map.of(
      CR_LABEL_KEY, parentController.getTsidString()
    );
    var datastoreMap = DatastoreConfRegistry.getDefault().dump();
    var sec = new SecretBuilder()
      .withNewMetadata()
      .withName(prefix + NAME_SECRET)
      .withNamespace(namespace)
      .withLabels(labels)
      .endMetadata()
      .withStringData(datastoreMap)
      .build();

    var envConfig = parentController.envConfig;
    var confMapData = Map.of(
      "OPRC_INVOKER_KAFKA", parentController.envConfig.kafkaBootstrap(),
      "OPRC_INVOKER_SA_URL", "http://%sstorage-adapter.%s.svc.cluster.local"
        .formatted(prefix, namespace),
      "OPRC_CRID", parentController.getTsidString(),
      "OPRC_INVOKER_PMHOST", envConfig.classManagerHost(),
      "OPRC_INVOKER_PMPORT", envConfig.classManagerPort(),
      "OPRC_INVOKER_CLSPROVISIONTOPIC", envConfig.clsTopic(),
      "OPRC_INVOKER_FNPROVISIONTOPIC", envConfig.fnTopic(),
      "OPRC_INVOKER_CRHASHTOPIC", envConfig.crHashTopic()
    );
    var confMap = new ConfigMapBuilder()
      .withNewMetadata()
      .withName(prefix + NAME_CONFIGMAP)
      .withNamespace(namespace)
      .withLabels(labels)
      .endMetadata()
      .withData(confMapData)
      .build();

    return List.of(confMap, sec);
  }

  @Override
  protected List<HasMetadata> doCreateAdjustOperation(CrAdjustmentPlan plan) {
    return List.of();
  }

  @Override
  public List<HasMetadata> doCreateDeleteOperation() {
    List<HasMetadata> toDeleteResource = Lists.mutable.empty();
    var confMap = kubernetesClient.configMaps()
      .inNamespace(namespace)
      .withName(prefix + NAME_CONFIGMAP).get();
    if (confMap!=null) toDeleteResource.add(confMap);
    var sec = kubernetesClient.secrets()
      .inNamespace(namespace)
      .withName(prefix + NAME_SECRET).get();
    if (sec!=null) toDeleteResource.add(sec);
    return toDeleteResource;
  }
}
