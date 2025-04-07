package org.hpcclab.oaas.crm.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.f4b6a3.tsid.Tsid;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.hpcclab.oaas.crm.exception.CrDeployException;
import org.hpcclab.oaas.crm.exception.CrUpdateException;
import org.hpcclab.oaas.crm.optimize.CrAdjustmentPlan;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;
import org.hpcclab.oaas.crm.template.CrTemplate;
import org.hpcclab.oaas.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class K8SCrController implements CrController {
  public static final String CR_LABEL_KEY = "cr-id";
  public static final String CR_COMPONENT_LABEL_KEY = "cr-part";
  public static final String CR_FN_KEY = "cr-fn";
  public static final String CR_ENV_ID_KEY = "cr-env-id";
  public static final String CR_TEMP_TYPE_KEY = "cr-template-type";
  public static final String NAME_SECRET = "secret";
  public static final String NAME_FUNCTION = "function";
  public static final String NAME_CONFIGMAP = "cm";
  private static final Logger logger = LoggerFactory.getLogger(K8SCrController.class);
  final long id;
  final String prefix;
  final CrTemplate template;
  final KubernetesClient kubernetesClient;
  final OprcEnvironment.EnvConfig envConfig;
  final Map<String, CrComponentController<HasMetadata>> componentControllers;
  final Map<String, ProtoOClass> attachedCls = Maps.mutable.empty();
  final Map<String, ProtoOFunction> attachedFn = Maps.mutable.empty();
  final Map<String, FnCrComponentController<HasMetadata>> fnControllers = Maps.mutable.empty();
  final FnCrControllerFactory<HasMetadata> factory;
  final Map<String, FuncRouting> routing = Maps.mutable.empty();
  CrDeploymentPlan currentPlan;
  boolean deleted = false;
  boolean initialized = false;
  ObjectMapper objectMapper;

  public K8SCrController(CrTemplate template,
                         KubernetesClient client,
                         Map<String, CrComponentController<HasMetadata>> componentControllers,
                         FnCrControllerFactory<HasMetadata> factory,
                         OprcEnvironment.EnvConfig envConfig,
                         Tsid tsid) {
    this.template = template;
    this.kubernetesClient = client;
    this.envConfig = envConfig;
    this.id = tsid.toLong();
    this.prefix = "cr-" + tsid.toLowerCase() + "-";
    this.factory = factory;
    this.componentControllers = componentControllers;
    for (CrComponentController<HasMetadata> componentController : componentControllers.values()) {
      componentController.init(this);
    }
    objectMapper = new ObjectMapper();

    objectMapper.registerModule(new ProtobufModule());
  }

  public K8SCrController(CrTemplate template,
                         KubernetesClient client,
                         Map<String, CrComponentController<HasMetadata>> componentControllers,
                         FnCrControllerFactory<HasMetadata> factory,
                         OprcEnvironment.EnvConfig envConfig,
                         ProtoCr protoCr) {
    this(template, client,
      componentControllers,
      factory,
      envConfig,
      Tsid.from(protoCr.getId())
    );
    for (ProtoOClass protoOClass : protoCr.getAttachedClsList()) {
      attachedCls.put(protoOClass.getKey(), protoOClass);
    }
    for (ProtoOFunction function : protoCr.getAttachedFnList()) {
      attachedFn.put(function.getKey(), function);
      FnCrComponentController<HasMetadata> controller = factory.create(function);
      controller.init(this);
      fnControllers.put(function.getKey(), controller);
    }
    var jsonDump = protoCr.getState().getJsonDump();
    if (!jsonDump.isEmpty()) {
      try {
        currentPlan = objectMapper.readValue(jsonDump, CrDeploymentPlan.class);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }
    deleted = protoCr.getDeleted();
    initialized = true;
  }

  @Override
  public long getId() {
    return id;
  }

  @Override
  public CrTemplate getTemplate() {
    return template;
  }

  @Override
  public Map<String, ProtoOClass> getAttachedCls() {
    return attachedCls;
  }

  @Override
  public Map<String, ProtoOFunction> getAttachedFn() {
    return attachedFn;
  }

  @Override
  public CrOperation createDeployOperation(CrDeploymentPlan plan, DeploymentUnit unit)
    throws CrDeployException {
    List<HasMetadata> resourceList = Lists.mutable.empty();
    ApplyK8SCrOperation crOperation = new ApplyK8SCrOperation(
      kubernetesClient,
      resourceList,
      () -> {
        attachedCls.put(unit.getCls().getKey(), unit.getCls());
        for (ProtoOFunction protoOFunction : unit.getFnListList()) {
          attachedFn.put(protoOFunction.getKey(), protoOFunction);
        }
        currentPlan = plan;
        componentControllers.values()
          .forEach(CrComponentController::updateStableTime);
        for (var controller : fnControllers.values()) {
          controller.updateStableTime();
        }
        initialized = true;
      });

    for (var componentController : componentControllers.values()) {
      resourceList.addAll(componentController.createDeployOperation(plan, unit));
    }
    for (ProtoOFunction fn : unit.getFnListList()) {
      var fnResourcePlan = deployFunction(plan, unit, fn);
      resourceList.addAll(fnResourcePlan.resources());
      crOperation.getFnUpdates().addAll(fnResourcePlan.fnUpdates());
    }

    return crOperation;
  }


  @Override
  public CrOperation createUpdateOperation(CrDeploymentPlan plan, DeploymentUnit unit) {
    List<HasMetadata> resources = Lists.mutable.empty();
    ApplyK8SCrOperation crOperation = new ApplyK8SCrOperation(kubernetesClient, resources, () -> {
      attachedCls.put(unit.getCls().getKey(), unit.getCls());
      for (var f : unit.getFnListList()) {
        attachedFn.put(f.getKey(), f);
      }
      currentPlan = plan;
    });

    for (var f : unit.getFnListList()) {
      var oldFunc = attachedFn.get(f.getKey());
      if (oldFunc!=null && oldFunc.equals(f))
        continue;
      FnResourcePlan fnResourcePlan = deployFunction(plan, unit, f);
      resources.addAll(fnResourcePlan.resources());
      crOperation.getFnUpdates().addAll(fnResourcePlan.fnUpdates());
    }
    return crOperation;
  }

  @Override
  public CrOperation createDetachOperation(ProtoOClass cls) throws CrUpdateException {
    if (attachedCls.size()==1 && attachedCls.containsKey(cls.getKey())) {
      return createDestroyOperation();
    }
    List<HasMetadata> resourceList = Lists.mutable.empty();
    for (ProtoFunctionBinding fb : cls.getFunctionsList()) {
      resourceList.addAll(removeFunction(fb.getFunction()));
    }
    return new DeleteK8SCrOperation(kubernetesClient, resourceList, () -> {
      attachedCls.remove(cls.getKey());
      for (ProtoFunctionBinding fb : cls.getFunctionsList()) {
        attachedFn.remove(fb.getFunction());
      }
    });
  }

  @Override
  public CrOperation createDestroyOperation() throws CrUpdateException {
    List<HasMetadata> toDeleteResource = Lists.mutable.empty();
    for (CrComponentController<HasMetadata> componentController : componentControllers.values()) {
      toDeleteResource.addAll(componentController.createDeleteOperation());
    }
    for (var controller : fnControllers.values()) {
      toDeleteResource.addAll(controller.createDeleteOperation());
    }
    return new DeleteK8SCrOperation(kubernetesClient, toDeleteResource,
      () -> {
        attachedCls.clear();
        attachedFn.clear();
        deleted = true;
      });
  }

  @Override
  public CrOperation createAdjustmentOperation(CrAdjustmentPlan adjustmentPlan) {
    List<HasMetadata> resource = Lists.mutable.empty();
    for (CrComponentController<HasMetadata> componentController : componentControllers.values()) {
      resource.addAll(componentController.createAdjustOperation(adjustmentPlan));
    }
    var crOperation = new AdjustmentCrOperation(
      kubernetesClient,
      resource,
      () -> currentPlan = currentPlan.update(adjustmentPlan)
    );
    for (var entry : adjustmentPlan.fnInstances().entrySet()) {
      if (!fnControllers.containsKey(entry.getKey()))
        continue;
      FnCrComponentController<HasMetadata> controller = fnControllers.get(entry.getKey());
      resource.addAll(controller.createAdjustOperation(adjustmentPlan));
      var optional = controller.buildStatusUpdate();
      if (optional.isPresent())
        crOperation.getFnUpdates().add(optional.get());
    }
    return crOperation;
  }

  @Override
  public CrDeploymentPlan currentPlan() {
    return currentPlan;
  }

  protected FnResourcePlan deployFunction(CrDeploymentPlan newPlan,
                                          DeploymentUnit unit,
                                          ProtoOFunction function) throws CrDeployException {
    FnCrComponentController<HasMetadata> fnController = factory.create(function);
    fnController.init(this);
    fnControllers.put(function.getKey(), fnController);
    List<HasMetadata> resources = fnController.createDeployOperation(newPlan, unit);
    var optional = fnController.buildStatusUpdate();
    if (optional.isPresent()) {
      var update = optional.get();
      updateRouting(function.getKey(), update.getStatus());
      return new FnResourcePlan(resources, List.of(update));
    } else {
      return new FnResourcePlan(resources, List.of());
    }
  }

  protected List<HasMetadata> removeFunction(String fnKey) throws CrUpdateException {
    if (fnControllers.containsKey(fnKey)) {
      CrComponentController<HasMetadata> controller = fnControllers.get(fnKey);
      return controller.createDeleteOperation();
    }
    return List.of();
  }

  @Override
  public ProtoCr dump() {
    String str;
    try {
      str = objectMapper.writeValueAsString(currentPlan);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    PartitionRouting partitionRouting = PartitionRouting.newBuilder()
      .putAllFunctions(routing)
      .build();
    return ProtoCr.newBuilder()
      .setId(id)
      .setEnv(envConfig.name())
      .setTemplate(template.name())
      .setNamespace(envConfig.namespace())
      .addAllAttachedCls(attachedCls.values())
      .addAllAttachedFn(attachedFn.values())
      .setState(ProtoCrState.newBuilder().setJsonDump(str).build())
      .setRouting(partitionRouting)
      .setDeleted(deleted)
      .build();
  }

  @Override
  public boolean isDeleted() {
    return deleted;
  }

  @Override
  public boolean isInitialized() {
    return initialized;
  }

  @Override
  public long getStableTime(String name) {
    if (componentControllers.containsKey(name))
      return componentControllers.get(name).getStableTime();
    if (fnControllers.containsKey(name))
      return fnControllers.get(name).getStableTime();
    return -1;
  }

  @Override
  public Optional<OFunctionStatusUpdate> updateFunctionStatus(String fnKey, ProtoOFunctionDeploymentStatus status) {
    ProtoOFunction func = getAttachedFn().get(fnKey);
    if (func==null) return Optional.empty();
    ProtoOFunction newFunc = func.toBuilder()
      .setStatus(status)
      .build();
    attachedFn.put(fnKey, newFunc);
    updateRouting(fnKey, status);

    return Optional.of(OFunctionStatusUpdate.newBuilder()
      .setKey(fnKey)
      .setStatus(status)
      .setProvision(newFunc.getProvision())
      .build());
  }

  private void updateRouting(String fnKey,ProtoOFunctionDeploymentStatus status) {
    if (status.getCondition() == ProtoDeploymentCondition.PROTO_DEPLOYMENT_CONDITION_RUNNING) {
      for (ProtoOClass cls : attachedCls.values()) {
        cls.getFunctionsList().stream()
          .filter(fb -> fb.getFunction().equals(fnKey))
          .forEach(fb -> {
            routing.put(fb.getName(), FuncRouting.newBuilder().setUrl(status.getInvocationUrl()).build());
          });
      }
    }
  }

  @Override
  public OprcEnvironment.EnvConfig getEnvConfig() {
    return envConfig;
  }
}
