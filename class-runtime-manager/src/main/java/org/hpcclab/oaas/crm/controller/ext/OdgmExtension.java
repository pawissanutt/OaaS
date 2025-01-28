package org.hpcclab.oaas.crm.controller.ext;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.hpcclab.oaas.crm.controller.AbstractK8sCrComponentController;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;
import org.hpcclab.oaas.proto.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Pawissanutt
 */
public class OdgmExtension implements CrComponentExtension {

  @Override
  public void applyOnCreate(List<HasMetadata> item,
                            CrDeploymentPlan plan,
                            AbstractK8sCrComponentController controller) {
    DataDistribution dist = plan.dataSpec().dist();
    var reqs = create(dist, controller);
    var json = asJson(reqs);
    for (HasMetadata hasMetadata : item) {
      if (hasMetadata instanceof Deployment deployment) {
        for (Container container : deployment.getSpec()
          .getTemplate()
          .getSpec()
          .getContainers()) {
          container.getEnv()
            .add(new EnvVar("ODGM_COLLECTION", json, null));
          container.getEnv()
            .add(new EnvVar("ODGM_NODE_ID", String.valueOf(dist.getNodeId()), null));
          String members = dist.getMembersList().stream()
            .map(String::valueOf)
              .collect(Collectors.joining(","));
          container.getEnv()
            .add(new EnvVar("ODGM_NODE_ID", members, null));
          container.getEnv()
            .add(new EnvVar("OPRC_ZENOH_PEERS", "tcp/router:17447", null));
        }
      }
    }

  }

  List<CreateCollectionRequest> create(DataDistribution dist,
                                       AbstractK8sCrComponentController controller) {
    var clsMap = controller.getParentController().getAttachedCls();
    var list = new ArrayList<CreateCollectionRequest>();
    for (var entry : dist.getCollectionsMap().entrySet()) {
      var builder = CreateCollectionRequest.newBuilder();
      builder.setName(entry.getKey());
      var partition = entry.getValue();
      builder.setPartitionCount(partition.getPartitionCount());
      builder.setReplicaCount(partition.getReplicaCount());
      builder.setShardType(partition.getShardType());
      builder.putAllOptions(partition.getOptionsMap());
      builder.addAllShardAssignments(partition.getAssignmentList());
      builder.setInvocations(createRoute(controller.getPrefix(), clsMap.get(entry.getKey()), controller.getParentController().getAttachedFn()));
      list.add(builder.build());
    }
    return list;
  }

  InvocationRoute createRoute(String prefix,
                              ProtoOClass cls,
                              Map<String, ProtoOFunction> fnMap) {
    var builder = InvocationRoute.newBuilder();
    for (var fb : cls.getFunctionsList()) {
      var name = fb.getName();
      var fn = fnMap.get(name);
      if (fn==null) continue;
      builder.putFnRoutes(name, FuncInvokeRoute.newBuilder()
        .setUrl(prefix + fn.getKey())
        .setStateless(fb.getNoMain())
        .build());
    }
    return builder.build();
  }

  String asJson(List<CreateCollectionRequest> reqs) {
    StringBuilder acc = new StringBuilder("[");
    boolean first = true;
    for (CreateCollectionRequest req : reqs) {
      if (!first) {
        acc.append(",");
      }
      try {
        String print = JsonFormat.printer().print(req);
        acc.append(print);
      } catch (InvalidProtocolBufferException e) {
        throw new RuntimeException(e);
      }
      first = false;
    }
    return acc.append("]").toString();
  }
}
