package org.hpcclab.oaas.crm.controller.ext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.hpcclab.oaas.crm.controller.AbstractK8sCrComponentController;
import org.hpcclab.oaas.crm.controller.K8SCrController;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;
import org.hpcclab.oaas.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Pawissanutt
 */
public class OdgmExtension implements CrComponentExtension {
  private static final Logger logger = LoggerFactory.getLogger(OdgmExtension.class);

  ObjectMapper objectMapper;

  public OdgmExtension() {

    objectMapper = new ObjectMapper()
      .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    objectMapper.registerModule(new ProtobufModule(ProtobufJacksonConfig.builder()
      .acceptLiteralFieldnames(true)
      .serializeLongsAsString(false)
      .build()));
  }

  @Override
  public void applyOnCreate(List<HasMetadata> item,
                            CrDeploymentPlan plan,
                            DeploymentUnit unit,
                            AbstractK8sCrComponentController controller) {
    DataDistribution dist = plan.dataSpec().dist();
    var reqs = create(dist, unit, controller);
    var json = asJson(reqs);
    logger.debug("collections: {}", json);
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
            .add(new EnvVar("ODGM_MEMBERS", members, null));
        }
      } else if (hasMetadata instanceof Service svc) {
        svc.getSpec()
          .getPorts()
          .add(new ServicePortBuilder()
            .withPort(17447)
            .withProtocol("TCP")
            .withTargetPort(new IntOrString(17447))
            .withName("zenoh")
            .build());
      }
    }

  }

  List<CreateCollectionRequest> create(DataDistribution dist,
                                       DeploymentUnit unit,
                                       AbstractK8sCrComponentController controller) {
    var list = new ArrayList<CreateCollectionRequest>();
    for (var entry : dist.getCollectionsMap().entrySet()) {
      var builder = CreateCollectionRequest.newBuilder();
      builder.setName(entry.getKey());
      var partition = entry.getValue();
      builder
        .setPartitionCount(partition.getPartitionCount())
        .setReplicaCount(partition.getReplicaCount())
        .setShardType(partition.getShardType())
        .putAllOptions(partition.getOptionsMap())
        .addAllShardAssignments(partition.getAssignmentList())
        .setInvocations(createRoute(controller.getPrefix(), unit.getCls(), controller.getParentController(), partition))
        .setShardType(partition.getShardType());
      logger.debug("partition: {}", partition);
      logger.debug("create collection request: {}", builder);
      list.add(builder.build());
    }
    return list;
  }

  InvocationRoute createRoute(String prefix,
                              ProtoOClass cls,
                              K8SCrController controller,
                              PartitionDistribution dist) {
    var namespace = controller.getEnvConfig().namespace();
    var builder = InvocationRoute.newBuilder();
    for (var fb : cls.getFunctionsList()) {
      if (dist.getDisabledFnsList().contains(fb.getName())) {
        continue;
      }
      var name = fb.getName();
      var standby = dist.getStandbyFnsList()
        .contains(fb.getName());
      logger.debug("function: {}, list: {}, standby: {}", name, dist.getStandbyFnsList(), standby);
      String fnSvc = fb.getFunction()
        .replaceAll("[._]", "-");
      String url;
      if (dist.getOptionsMap().getOrDefault("knative_use_private", "false").equals("true")) {
        url = "http://" + prefix + "fn-" + fnSvc + "-00001-private." + namespace + ".svc.cluster.local";
      } else {
        url = "http://" + prefix + "fn-" + fnSvc + "." + namespace + ".svc.cluster.local";
      }
      builder.putFnRoutes(name, FuncInvokeRoute.newBuilder()
        .setUrl(url)
        .setStateless(fb.getNoMain())
        .setStandby(standby)
        .build());
    }
    return builder.build();
  }

  String asJson(List<CreateCollectionRequest> reqs) {
    try {
      return objectMapper.writeValueAsString(reqs);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
