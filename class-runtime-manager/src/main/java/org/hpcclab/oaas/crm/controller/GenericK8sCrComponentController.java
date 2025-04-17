package org.hpcclab.oaas.crm.controller;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import org.eclipse.collections.api.factory.Lists;
import org.hpcclab.oaas.crm.CrtMappingConfig;
import org.hpcclab.oaas.crm.controller.ext.CrComponentExtension;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.hpcclab.oaas.crm.optimize.CrAdjustmentPlan;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;
import org.hpcclab.oaas.crm.optimize.CrInstanceSpec;
import org.hpcclab.oaas.proto.DeploymentUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.hpcclab.oaas.crm.controller.K8SCrController.CR_COMPONENT_LABEL_KEY;
import static org.hpcclab.oaas.crm.controller.K8SCrController.CR_LABEL_KEY;

public class GenericK8sCrComponentController extends AbstractK8sCrComponentController {
  final String serviceName;

  private static final Logger logger = LoggerFactory.getLogger( GenericK8sCrComponentController.class );

  public GenericK8sCrComponentController(CrtMappingConfig.CrComponentConfig svcConfig,
                                         OprcEnvironment.EnvConfig envConfig,
                                         String name) {
    super(svcConfig, envConfig);
    this.serviceName = name;
  }

  @Override
  protected List<HasMetadata> doCreateDeployOperation(CrDeploymentPlan plan, DeploymentUnit unit) {
    var instanceSpec = plan.coreInstances().get(serviceName);
    if (instanceSpec==null || instanceSpec.disable()) return List.of();
    var labels = Map.of(
      CR_LABEL_KEY, parentController.getTsidString(),
      CR_COMPONENT_LABEL_KEY, serviceName
    );
    String name = prefix + this.serviceName;
    var deployment = createDeployment(instanceSpec, name, labels);

    var svc = createSvc(name, labels);

    var resources = Lists.mutable.<HasMetadata>of(
      deployment, svc
    );
    if (instanceSpec.enableHpa()) {
      var hpa = createHpa(instanceSpec, labels, name, name);
      resources.add(hpa);
    }
//    for (CrComponentExtension extension : extensions) {
//      extension.applyOnCreate(resources, plan,unit, this);
//    }
    return resources;
  }

  Service createSvc(String name, Map<String, String> labels) {
    ServiceBuilder serviceBuilder = new ServiceBuilder().withNewMetadata()
      .withNamespace(this.envConfig.namespace())
      .withName(name)
      .withLabels(labels)
      .endMetadata();
    int port = svcConfig.exposePort()==0 ? 8080:svcConfig.exposePort();
    serviceBuilder.withNewSpec()
      .addToSelector(labels)
      .addNewPort()
      .withName("http")
      .withPort(port)
      .withTargetPort(new IntOrString(port))
      .withProtocol("TCP")
      .endPort()
      .endSpec();
    return serviceBuilder.build();
  }

  Deployment createDeployment(CrInstanceSpec instanceSpec,
                              String name, Map<String, String> labels) {
    var builder = new DeploymentBuilder();
    builder.withNewMetadata()
      .withNamespace(this.envConfig.namespace())
      .withName(name)
      .withLabels(labels)
      .endMetadata();

    ContainerBuilder containerBuilder = new ContainerBuilder()
      .withName("app")
      .withImage(this.svcConfig.image())
      .withImagePullPolicy(this.svcConfig.imagePullPolicy())
      .withResources(K8sResourceUtil.makeResourceRequirements(instanceSpec))
      .withEnv(K8sResourceUtil.makeEnv(svcConfig.env()));
    if (svcConfig.imagePullPolicy()!=null && !svcConfig.imagePullPolicy().isEmpty())
      containerBuilder.withImagePullPolicy(svcConfig.imagePullPolicy());
    Container container = containerBuilder.build();
    builder.withNewSpec()
      .withReplicas(instanceSpec.minInstance())
      .withNewSelector()
      .addToMatchLabels(labels).endSelector()
      .withNewTemplate()
      .withNewMetadata()
      .withLabels(labels)
      .endMetadata()
      .withNewSpec()
      .withNodeSelector(envConfig.nodeSelector() == null ? Map.of():envConfig.nodeSelector())
      .withContainers(container)
      .endSpec()
      .endTemplate()
      .endSpec();
    return builder.build();
  }

  @Override
  protected List<HasMetadata> doCreateAdjustOperation(CrAdjustmentPlan plan) {
    var instanceSpec = plan.coreInstances().get(this.serviceName);
    if (instanceSpec==null) return List.of();
    String name = prefix + this.serviceName;
    if (instanceSpec.enableHpa()) {
      HorizontalPodAutoscaler hpa = editHpa(instanceSpec, name);
      List<HasMetadata> resources = (hpa==null ? List.of():List.of(hpa));
      for (CrComponentExtension extension : extensions) {
        extension.applyOnAdjust(resources, plan, this);
      }
      return resources;
    } else {
      Deployment deployment = kubernetesClient.apps().deployments()
        .inNamespace(envConfig.namespace())
        .withName(name)
        .get();
      deployment.getSpec()
        .setReplicas(instanceSpec.minInstance());

      List<HasMetadata> resources = List.of(deployment);
      for (CrComponentExtension extension : extensions) {
        extension.applyOnAdjust(resources, plan, this);
      }
      return resources;
    }
  }

  @Override
  protected List<HasMetadata> doCreateDeleteOperation() {
    List<HasMetadata> toDeleteResource = Lists.mutable.empty();
    var labels = Map.of(
      CR_LABEL_KEY, parentController.getTsidString(),
      CR_COMPONENT_LABEL_KEY, serviceName
    );
    var depList = kubernetesClient.apps()
      .deployments()
      .inNamespace(envConfig.namespace())
      .withLabels(labels)
      .list()
      .getItems();
    logger.debug("remove {}", depList.stream().map(HasMetadata::getMetadata).map(ObjectMeta::getName).toList());
    toDeleteResource.addAll(depList);
    var svcList = kubernetesClient.services()
      .inNamespace(envConfig.namespace())
      .withLabels(labels)
      .list()
      .getItems();
    toDeleteResource.addAll(svcList);
    var hpa = kubernetesClient.autoscaling().v2().horizontalPodAutoscalers()
      .inNamespace(envConfig.namespace())
      .withLabels(labels)
      .list().getItems();
    toDeleteResource.addAll(hpa);
    for (CrComponentExtension extension : extensions) {
      extension.applyOnDelete(toDeleteResource, this);
    }
    return toDeleteResource;
  }

}
