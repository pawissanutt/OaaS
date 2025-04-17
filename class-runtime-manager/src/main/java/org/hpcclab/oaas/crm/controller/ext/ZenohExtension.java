package org.hpcclab.oaas.crm.controller.ext;

import io.fabric8.knative.serving.v1.Service;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.hpcclab.oaas.crm.controller.AbstractK8sCrComponentController;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;
import org.hpcclab.oaas.proto.DeploymentUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Pawissanutt
 */
public class ZenohExtension implements CrComponentExtension {
  String router;
  List<String> peers;

  public ZenohExtension(Map<String, String> options) {
    router = options.getOrDefault("router", "tcp/router:17447");
    var peersStr = options.get("peers");
    peers = new ArrayList<>();
    if (peersStr != null && !options.get("peers").isEmpty()) {
      peers.addAll(Arrays.asList(options.get("peers").split(",")));
    }
  }

  @Override
  public void applyOnCreate(List<HasMetadata> items,
                            CrDeploymentPlan plan,
                            DeploymentUnit unit,
                            AbstractK8sCrComponentController controller) {
    StringBuilder peersConnection = new StringBuilder(router);
    for (String peer : peers) {
      peersConnection.append(",tcp/").append(controller.getPrefix()).append(peer);
    }
    for (HasMetadata item : items) {
      if (item instanceof Service svc) {
        List<Container> containers = svc
          .getSpec()
          .getTemplate()
          .getSpec().getContainers();

        for (Container container : containers) {
          container.getEnv()
            .add(
              new EnvVar("OPRC_ZENOH_PEERS", peersConnection.toString(), null)
            );
        }
      } else if (item instanceof Deployment deployment) {
        List<Container> containers = deployment
          .getSpec()
          .getTemplate()
          .getSpec().getContainers();

        for (Container container : containers) {
          container.getEnv()
            .add(
              new EnvVar("OPRC_ZENOH_PEERS", peersConnection.toString(), null)
            );
        }
      }
    }
  }
}
