package org.hpcclab.oaas.crm.controller;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import org.eclipse.collections.api.factory.Lists;
import org.hpcclab.oaas.crm.CrtMappingConfig;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.hpcclab.oaas.crm.optimize.CrAdjustmentPlan;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;

import java.util.List;
import java.util.Map;

import static org.hpcclab.oaas.crm.CrComponent.STORAGE_ADAPTER;
import static org.hpcclab.oaas.crm.controller.K8SCrController.CR_COMPONENT_LABEL_KEY;
import static org.hpcclab.oaas.crm.controller.K8SCrController.CR_LABEL_KEY;

/**
 * @author Pawissanutt
 */
@Deprecated
public class SaK8sCrComponentController extends AbstractK8sCrComponentController {
  public SaK8sCrComponentController(CrtMappingConfig.CrComponentConfig svcConfig,
                                    OprcEnvironment.Config envConfig) {
    super(svcConfig, envConfig);
  }


  public List<HasMetadata> doCreateDeployOperation(CrDeploymentPlan plan) {
    var instanceSpec = plan.coreInstances().get(STORAGE_ADAPTER);
    if (instanceSpec == null || instanceSpec.disable()) return List.of();
    var labels = Map.of(
      CR_LABEL_KEY, parentController.getTsidString(),
      CR_COMPONENT_LABEL_KEY, STORAGE_ADAPTER.getSvc()
    );
    String name = prefix + STORAGE_ADAPTER.getSvc();
    var deployment = createDeployment(
      "/crts/storage-adapter-dep.yml",
      name,
      labels,
      instanceSpec
    );
//    attachSecret(deployment, prefix + NAME_SECRET);
//    attachConf(deployment, prefix + NAME_CONFIGMAP);
    var svc = createSvc(
      "/crts/storage-adapter-svc.yml",
      name,
      labels);

    var resources = Lists.mutable.<HasMetadata>of(
      deployment, svc
    );
    if (instanceSpec.enableHpa()) {
      var hpa = createHpa(instanceSpec, labels, name, name);
      resources.add(hpa);
    }

    return resources;
  }

  @Override
  protected List<HasMetadata> doCreateAdjustOperation(CrAdjustmentPlan plan) {
    var instanceSpec = plan.coreInstances().get(STORAGE_ADAPTER);
    if (instanceSpec == null || instanceSpec.disable())
      return List.of();
    String name = prefix + STORAGE_ADAPTER.getSvc();
    if (instanceSpec.enableHpa()) {
      HorizontalPodAutoscaler hpa = editHpa(instanceSpec, name);
      return hpa==null ? List.of():List.of(hpa);
    } else {
      Deployment deployment = kubernetesClient.apps().deployments()
        .inNamespace(namespace)
        .withName(name)
        .get();
      deployment.getSpec()
        .setReplicas(instanceSpec.minInstance());
      return List.of(deployment);
    }
  }

  @Override
  public List<HasMetadata> doCreateDeleteOperation() {
    List<HasMetadata> toDeleteResource = Lists.mutable.empty();
    String tsidString = parentController.getTsidString();
    Map<String, String> labels = Map.of(
      CR_LABEL_KEY, tsidString,
      CR_COMPONENT_LABEL_KEY, STORAGE_ADAPTER.getSvc()
    );
    var depList = kubernetesClient.apps().deployments()
      .withLabels(labels)
      .list()
      .getItems();
    toDeleteResource.addAll(depList);
    var svcList = kubernetesClient.services()
      .withLabels(labels)
      .list()
      .getItems();
    toDeleteResource.addAll(svcList);
    var hpa = kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
      .withLabels(labels)
      .list().getItems();
    toDeleteResource.addAll(hpa);
    return toDeleteResource;
  }
}
