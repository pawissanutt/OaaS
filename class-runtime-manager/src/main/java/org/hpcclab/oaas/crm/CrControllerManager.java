package org.hpcclab.oaas.crm;

import com.github.f4b6a3.tsid.Tsid;
import io.quarkus.grpc.GrpcClient;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.hpcclab.oaas.crm.controller.CrController;
import org.hpcclab.oaas.crm.env.EnvironmentManager;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.hpcclab.oaas.crm.template.CrTemplateManager;
import org.hpcclab.oaas.proto.CrStateServiceGrpc.CrStateServiceBlockingStub;
import org.hpcclab.oaas.proto.DeploymentStatusUpdaterGrpc.DeploymentStatusUpdaterBlockingStub;
import org.hpcclab.oaas.proto.*;
import org.hpcclab.oaas.proto.InternalCrStateServiceGrpc.InternalCrStateServiceBlockingStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ApplicationScoped
public class CrControllerManager {
  private static final Logger logger = LoggerFactory.getLogger(CrControllerManager.class);
  final InternalCrStateServiceBlockingStub crStateUpdater;
  final CrStateServiceBlockingStub crStateService;
  final DeploymentStatusUpdaterBlockingStub deploymentStatusUpdater;
  final CrTemplateManager templateManager;
  final EnvironmentManager environmentManager;
  final Vertx vertx;
  Map<Long, CrController> controllerMap = ConcurrentHashMap.newMap();

  @Inject
  public CrControllerManager(
    @GrpcClient("package-manager") InternalCrStateServiceBlockingStub crStateUpdater,
    @GrpcClient("package-manager") CrStateServiceBlockingStub crStateService,
    @GrpcClient("package-manager") DeploymentStatusUpdaterBlockingStub deploymentStatusUpdater,
    CrTemplateManager templateManager,
    EnvironmentManager environmentManager,
    Vertx vertx) {

    this.crStateUpdater = crStateUpdater;
    this.crStateService = crStateService;
    this.deploymentStatusUpdater = deploymentStatusUpdater;
    this.templateManager = templateManager;
    this.environmentManager = environmentManager;
    this.vertx = vertx;
  }

  public void loadAllToLocal() {
    var crs = crStateService.selectFromEnv(EnvSelector.newBuilder().addAllEnv(environmentManager.getEnvironment().managedEnvs())
      .build());
    var env = environmentManager.getEnvironmentConfig();
    while (crs.hasNext()) {
      var protoCr = crs.next();
      var controller = templateManager.load(env, protoCr);
      controllerMap.put(controller.getId(), controller);
      if (logger.isInfoEnabled())
        logger.info("loaded CR to local: [{}, cls={}]",
          controller.getId(),
          controller.getAttachedCls()
        );
    }
  }

  public CrController get(long id) {
    return controllerMap.get(id);
  }

  public CrController getOrLoad(long id, OprcEnvironment env) {
    var controller = controllerMap.get(id);
    if (controller==null) {
      var protoCr = crStateService.get(SingleKeyQuery.newBuilder().setKey(Tsid.from(id).toLowerCase()).build());
      if (protoCr.getId()==0) return null;
      controller = templateManager.load(env.config(), protoCr);
      controllerMap.put(id, controller);
    }
    return controller;
  }

  public CrController getOrLoad(ProtoCr protoCr, OprcEnvironment env) {
    var cr = controllerMap.get(protoCr.getId());
    if (cr==null) {
      if (protoCr.getId()==0) return null;
      cr = templateManager.load(env.config(), protoCr);
      controllerMap.put(protoCr.getId(), cr);
    }
    return cr;
  }

  public CrController create(OprcEnvironment env,
                             DeploymentUnit deploymentUnit) {
    var template = templateManager.selectTemplate(deploymentUnit);
    logger.info("select template '{}' for class '{}'",
      template.name(), deploymentUnit.getCls().getKey());
    var controller = template.create(env.config(), deploymentUnit);
    saveToLocal(controller);
    return controller;
  }

  public void saveToLocal(CrController orbit) {
    controllerMap.put(orbit.getId(), orbit);
  }

  public void saveToRemote(CrController controller) {
    saveToLocal(controller);
    ProtoCr cr = controller.dump();
    logger.debug("update CR to remote {}", cr);
    crStateUpdater.updateCr(cr);
  }

  public void deleteFromLocal(CrController controller) {
    controllerMap.remove(controller.getId());
  }

  public void update(String crId,
                     String fnKey,
                     ProtoOFunctionDeploymentStatus status) {
    update(crId, fnKey, status, 3);
  }

  public void update(String crId,
                     String fnKey,
                     ProtoOFunctionDeploymentStatus status,
                     int count) {
    if (count==0) return;
    var id = Tsid.from(crId).toLong();
    var controller = get(id);
    if (controller!=null && controller.isInitialized()) {
      vertx.executeBlockingAndForget(() -> {
        var option = controller.updateFunctionStatus(fnKey, status);

        option.ifPresent(request -> {
          // deprecated
          deploymentStatusUpdater.updateFn(request);
          saveToRemote(controller);
        });
        return 0;
      });
    } else {
      vertx.setTimer(500, l -> update(crId, fnKey, status, count - 1));
    }
  }
}
