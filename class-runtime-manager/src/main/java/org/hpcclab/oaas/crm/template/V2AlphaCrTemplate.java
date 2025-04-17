package org.hpcclab.oaas.crm.template;

import com.github.f4b6a3.tsid.Tsid;
import io.fabric8.kubernetes.api.model.HasMetadata;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.hpcclab.oaas.crm.CrControllerManager;
import org.hpcclab.oaas.crm.CrmConfig;
import org.hpcclab.oaas.crm.CrtMappingConfig;
import org.hpcclab.oaas.crm.CrtMappingConfig.CrtConfig;
import org.hpcclab.oaas.crm.controller.*;
import org.hpcclab.oaas.crm.controller.ext.OdgmExtension;
import org.hpcclab.oaas.crm.controller.ext.ZenohExtension;
import org.hpcclab.oaas.crm.env.EnvironmentManager;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.hpcclab.oaas.crm.filter.K8sFilterFactory;
import org.hpcclab.oaas.crm.optimize.QosOptimizer;
import org.hpcclab.oaas.proto.DeploymentUnit;
import org.hpcclab.oaas.proto.ProtoCr;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.hpcclab.oaas.crm.CrComponent.CONFIG;

public class V2AlphaCrTemplate extends AbstractCrTemplate {
  final K8sFilterFactory filterFactory;

  public V2AlphaCrTemplate(String name,
                           EnvironmentManager environmentManager,
                           Function<CrtConfig, QosOptimizer> optimizerBuilder,
                           CrtConfig config,
                           CrmConfig crmConfig) {
    super(name, environmentManager, config, optimizerBuilder, crmConfig);
    filterFactory = new K8sFilterFactory();
  }

  @Override
  public void init(CrControllerManager crControllerManager,
                   EnvironmentManager environmentManager) {
    //    FnEventObserver fnEventObserver = FnEventObserver.getOrCreate(
//      type(),
//      new DefaultKnativeClient(k8sClient),
//      crControllerManager,
//      environmentManager
//    );
//    fnEventObserver.start(Map.of(K8SCrController.CR_TEMP_TYPE_KEY, type()));
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
    Tsid id;
    if (deploymentUnit.getCrId()!=0) {
      id = Tsid.from(deploymentUnit.getCrId());
    } else {
      id = tsidFactory.create();
    }
    return new K8SCrController(
      this,
      environmentManager.getK8sClient(envConf.name()),
      componentControllers,
      factory,
      envConf,
      id
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
    MutableMap<String, CrComponentController<HasMetadata>> map = Maps.mutable
      .of(CONFIG.getSvc(), conf);
    for (var entry : this.config.services().entrySet()) {
      map.put(entry.getKey(), createGeneric3c(entry.getKey(), entry.getValue(), envConf));
    }
    return map;
  }

  private GenericK8sCrComponentController createGeneric3c(String name,
                                                          CrtMappingConfig.CrComponentConfig svcConfig,
                                                          OprcEnvironment.EnvConfig envConf) {
    var controller = new GenericK8sCrComponentController(svcConfig, envConf, name);
    for (var ext: svcConfig.extensions()) {
      if (Objects.equals(ext.name(), "odgm")) {
        controller.addExtension(new OdgmExtension());
      }
      if (Objects.equals(ext.name(), "zenoh")) {
        controller.addExtension(new ZenohExtension(ext.options()));
      }
    }
    filterFactory.injectFilter(svcConfig.filters(), controller);
    return controller;
  }


  @Override
  public String type() {
    return "v2alpha";
  }
}
