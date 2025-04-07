package org.hpcclab.oaas.crm.template;

import io.fabric8.knative.client.DefaultKnativeClient;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.hpcclab.oaas.crm.CrControllerManager;
import org.hpcclab.oaas.crm.CrmConfig;
import org.hpcclab.oaas.crm.CrtMappingConfig;
import org.hpcclab.oaas.crm.CrtMappingConfig.CrtConfig;
import org.hpcclab.oaas.crm.controller.*;
import org.hpcclab.oaas.crm.env.EnvironmentManager;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.hpcclab.oaas.crm.filter.K8sFilterFactory;
import org.hpcclab.oaas.crm.filter.PodMonitorInjectingFilter;
import org.hpcclab.oaas.crm.observe.FnEventObserver;
import org.hpcclab.oaas.crm.optimize.QosOptimizer;
import org.hpcclab.oaas.proto.DeploymentUnit;
import org.hpcclab.oaas.proto.ProtoCr;

import java.util.Map;
import java.util.function.Function;

import static org.hpcclab.oaas.crm.CrComponent.*;

public class V1CrTemplate extends AbstractCrTemplate {
  final K8sFilterFactory filterFactory;


  public V1CrTemplate(String name,
                      EnvironmentManager environmentManager,
                      Function<CrtConfig, QosOptimizer> optimizerBuilder,
                      CrtConfig config,
                      CrmConfig crmConfig) {
    super(name, environmentManager, config, optimizerBuilder, crmConfig);
    filterFactory = new K8sFilterFactory();
  }

  @Override
  public void init(CrControllerManager crControllerManager, EnvironmentManager environmentManager) {
    var client = environmentManager.getK8sClient(environmentManager.getEnvironment()
      .getManagedEnvs().keySet().stream().findFirst().orElseThrow()
    );
    FnEventObserver fnEventObserver = FnEventObserver.getOrCreate(
      type(),
      new DefaultKnativeClient(client),
      crControllerManager,
      environmentManager
    );
    fnEventObserver.start(Map.of(K8SCrController.CR_TEMP_TYPE_KEY, type()));
  }

  @Override
  public CrtConfig getConfig() {
    return config;
  }

  @Override
  public CrController create(OprcEnvironment.EnvConfig envConf, DeploymentUnit deploymentUnit) {
    Map<String, CrComponentController<HasMetadata>> componentControllers =
      createComponentControllers(envConf);
    var factory = new UnifyFnCrControllerFactory(config.functions(), envConf);
    filterFactory.injectFilter(config.functions().filters(), factory);
    return new K8SCrController(
      this,
      environmentManager.getK8sClient(deploymentUnit.getEnv()),
      componentControllers,
      factory,
      envConf,
      tsidFactory.create()
    );
  }

  @Override
  public CrController load(OprcEnvironment.EnvConfig envConf, ProtoCr cr) {
    Map<String, CrComponentController<HasMetadata>> componentControllers = createComponentControllers(envConf);
    var fnCrControllerFactory = new UnifyFnCrControllerFactory(config.functions(), envConf);
    filterFactory.injectFilter(config.functions().filters(), fnCrControllerFactory);
    return new K8SCrController(
      this,
      environmentManager.getK8sClient(envConf.name()),
      componentControllers,
      fnCrControllerFactory,
      envConf,
      cr
    );
  }

  private Map<String, CrComponentController<HasMetadata>> createComponentControllers(OprcEnvironment.EnvConfig envConf) {
    var conf = new ConfigK8sCrComponentController(null, envConf);
    var invoker = createInvoker3c(envConf);
    var sa = createSa3c(envConf);
    MutableMap<String, CrComponentController<HasMetadata>> map = Maps.mutable
      .of( CONFIG.getSvc(), conf);
    if (invoker != null) map.put(INVOKER.getSvc(), invoker);
    if (sa != null) map.put(STORAGE_ADAPTER.getSvc(), sa);
    return map;
  }

  private SaK8sCrComponentController createSa3c(OprcEnvironment.EnvConfig envConf) {
    CrtMappingConfig.CrComponentConfig svcConfig = config.services().get(STORAGE_ADAPTER.getSvc());
    if (svcConfig == null) return null;
    SaK8sCrComponentController sa = new SaK8sCrComponentController(
      svcConfig, envConf);
    filterFactory.injectFilter(svcConfig.filters(), sa);
    return sa;
  }

  private InvokerK8sCrComponentController createInvoker3c(OprcEnvironment.EnvConfig envConf) {
    CrtMappingConfig.CrComponentConfig svcConfig = config.services().get(INVOKER.getSvc());
    if (svcConfig == null) return null;
    var invoker = new InvokerK8sCrComponentController(svcConfig, envConf);
    if (!crmConfig.monitorDisable()) {
      invoker.addFilter(new PodMonitorInjectingFilter(environmentManager.getK8sClient(envConf.name())));
    }
    filterFactory.injectFilter(svcConfig.filters(), invoker);
    return invoker;
  }



  @Override
  public String type() {
    return "default";
  }
}
