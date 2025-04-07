package org.hpcclab.oaas.pm.deploy;

import com.github.f4b6a3.tsid.TsidCreator;
import io.smallrye.mutiny.tuples.Tuple2;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.collections.api.factory.Lists;
import org.hpcclab.oaas.arango.repo.GenericArgRepository;
import org.hpcclab.oaas.mapper.ProtoMapper;
import org.hpcclab.oaas.model.cls.OClass;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.hpcclab.oaas.model.function.FunctionBinding;
import org.hpcclab.oaas.model.function.OFunction;
import org.hpcclab.oaas.model.pkg.OClassDeployment;
import org.hpcclab.oaas.model.pkg.OPackage;
import org.hpcclab.oaas.model.qos.ConsistencyModel;
import org.hpcclab.oaas.pm.PkgManagerConfig;
import org.hpcclab.oaas.pm.service.CrStateManager;
import org.hpcclab.oaas.pm.service.EnvironmentRegistry;
import org.hpcclab.oaas.pm.service.PackagePublisher;
import org.hpcclab.oaas.proto.DataDistribution;
import org.hpcclab.oaas.proto.DeploymentUnit;
import org.hpcclab.oaas.proto.PartitionDistribution;
import org.hpcclab.oaas.proto.ShardAssignment;
import org.hpcclab.oaas.repository.FunctionRepository;
import org.hpcclab.oaas.repository.PackageDeployer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Pawissanutt
 */
@ApplicationScoped
public class ClassDeploymentManager implements PackageDeployer {
  private static final Logger logger = LoggerFactory.getLogger(ClassDeploymentManager.class);
  @Inject
  GenericArgRepository<OClassDeployment> repo;
  @Inject
  EnvironmentRegistry registry;
  @Inject
  CrStateManager crStateManager;
  @Inject
  ProtoMapper protoMapper;
  @Inject
  FunctionRepository funcRepo;
  @Inject
  PackagePublisher packagePublisher;
  @Inject
  PkgManagerConfig config;

  public GenericArgRepository<OClassDeployment> getRepo() {
    return repo;
  }

  void reassign(OClassDeployment deployment) {
    var partitionCount = Math.max(1, deployment.getPartitionCount());
    deployment.setPartitionCount(partitionCount);
    var replicaCount = Math.max(1, deployment.getReplicaCount());
    deployment.setReplicaCount(replicaCount);
    var possibleEnv = registry.getEnvConfig().environments()
      .stream()
      .map(EnvironmentRegistry.Environment::name)
      .toList();
    if (deployment.getTargetEnvs().isEmpty()) {
      deployment.setTargetEnvs(List.of(registry.getEnvConfig().defaultEnv()));
    } else {
      for (String env : deployment.getTargetEnvs()) {
        if (!possibleEnv.contains(env)) {
          throw StdOaasException.format("no environment '%s' exists", env);
        }
      }
    }
    if (deployment.getMembers().isEmpty()) {
      var envs = deployment.getTargetEnvs();
      var members = envs.stream()
        .map(env -> new OClassDeployment.MemberGroup()
          .setId(generateId())
          .setEnv(env)
        )
        .toList();
      deployment.setMembers(members);
    }
    if (deployment.getAssignments().isEmpty()) {
      var completeMembers = deployment.getMembers().stream()
        .filter(OClassDeployment.MemberGroup::isAllPartitions)
        .map(OClassDeployment.MemberGroup::getId)
        .toList();
      var members = deployment.getMembers().stream()
        .filter(m -> !m.isAllPartitions())
        .map(m ->
          Tuple2.of(m.getId(), m.getMaxShards() < 0? Integer.MAX_VALUE:m.getMaxShards()))
        .collect(Collectors.toCollection(LinkedList::new));
      var assignments = new ArrayList<OClassDeployment.ShardAssignment>();
      for (int i = 0; i < partitionCount; i++) {
        var assignment = new OClassDeployment.ShardAssignment();
        var owners = new ArrayList<>(completeMembers);
        var shardIds = new ArrayList<Long>();
        for (int j = owners.size(); j < replicaCount; j++) {
          var m = members.removeFirst();
          while (m.getItem2() == 0 && !members.isEmpty()) {
            m = members.removeFirst();
          }
          if (m.getItem2() == 0) {
            throw StdOaasException.format("not enough members to assign shards");
          }
          owners.add(m.getItem1());
          if (m.getItem2()> 0) {
            members.add(Tuple2.of(m.getItem1(), m.getItem2() - 1));
          }
        }
        for (int j = 0; j < owners.size(); j++) {
          shardIds.add(generateId());
        }
        assignment.setPrimary(shardIds.getFirst());
        assignment.setOwners(owners);
        assignment.setShardIds(shardIds);
        assignments.add(assignment);
      }
      deployment.setAssignments(assignments);
    }
  }

  long generateId() {
    return TsidCreator.getTsid1024().toLong();
  }


  @Override
  public void deploy(OPackage pkg) {
    for (var deploy : pkg.getDeployments()) {
      var oldDeploy = repo.get(deploy.getKey());
      if (oldDeploy!=null)
        continue;
      var clsOp = pkg.getClasses().stream()
        .filter(c -> c.getKey().equals(deploy.getKey()))
        .findFirst();
      if (clsOp.isPresent()) {
        reassign(deploy);
        deployToEnvs(deploy, clsOp.get(), pkg);
      }
    }
    if (config.kafkaEnabled()) {
      packagePublisher.submitNewPkg(pkg).await().indefinitely();
    }
  }

  void deployToEnvs(OClassDeployment deployment,
                    OClass cls,
                    OPackage pkg) {
    for (var member : deployment.getMembers()) {
      var unit = createDeploymentUnit(deployment, cls, pkg, member);
      crStateManager.deploy(member.getEnv(), unit);
    }
    logger.debug("persist {}", deployment.getKey());
    repo.persist(deployment);
  }

  @Override
  public void detach(String clsKey) {
    var deploy = repo.get(clsKey);
    if (deploy==null) {
      return;
    }
    logger.debug("detach {} {} ", clsKey, deploy.getMembers());
    for (var member : deploy.getMembers()) {
      crStateManager.undeploy(member.getEnv(), member.getId());
    }
    repo.remove(clsKey);
  }



  DeploymentUnit createDeploymentUnit(OClassDeployment deploy,
                                      OClass cls,
                                      OPackage pkg,
                                      OClassDeployment.MemberGroup member) {
    var resolvedFnList = cls.getResolved()
      .getFunctions().values()
      .stream()
      .map(FunctionBinding::getFunction)
      .collect(Collectors.toSet());
    List<OFunction> fnList = Lists.mutable.empty();
    List<String> fnToLoad = Lists.mutable.empty();
    for (String key : resolvedFnList) {
      Optional<OFunction> fnOptional = pkg.getFunctions().stream()
        .filter(f -> f.getKey().equals(key))
        .findAny();
      if (fnOptional.isPresent()) fnList.add(fnOptional.get());
      else fnToLoad.add(key);
    }
    fnList.addAll(funcRepo.list(fnToLoad)
      .values());
    var protoFnList = fnList.stream()
      .map(protoMapper::toProto)
      .toList();
    return DeploymentUnit.newBuilder()
      .setCrId(member.getId())
      .setEnv(member.getEnv())
      .setCls(protoMapper.toProto(cls))
      .addAllFnList(protoFnList)
      .setDist(toDist(deploy, cls, member))
      .build();
  }

  DataDistribution.Builder toDist(OClassDeployment deploy,
                                  OClass cls,
                                  OClassDeployment.MemberGroup member) {
    var dist = DataDistribution.newBuilder();
    var members = deploy.getMembers()
      .stream()
      .map(OClassDeployment.MemberGroup::getId)
      .toList();
    dist.addAllMembers(members);
    List<ShardAssignment> shardAssignments = deploy.getAssignments().stream()
      .map(assignment -> {
        var shard = ShardAssignment.newBuilder()
          .addAllReplica(assignment.getOwners())
          .addAllShardIds(assignment.getShardIds());
        if (assignment.getPrimary() > 0) {
          shard.setPrimary(assignment.getPrimary());
        }
        return shard.build();
      })
      .toList();
    logger.debug("standby: {}", member.getStandbyFn());
    var partDist = generatePartDist(deploy, cls, member);
    partDist.addAllAssignment(shardAssignments);
    dist.putCollections(deploy.getKey(), partDist.build());
    dist.setNodeId(member.getId());
    return dist;
  }

  private PartitionDistribution.Builder generatePartDist(OClassDeployment deploy,
                                                         OClass cls,
                                                         OClassDeployment.MemberGroup member) {

    var partDist = PartitionDistribution.newBuilder();
    String shardType = deploy.getShardType();
    if (shardType == null || shardType.isEmpty()) {
      ConsistencyModel consistency = cls.getConstraints().consistency();
      shardType = switch (consistency) {
        case null -> "basic";
        case NONE -> "basic";
        case READ_YOUR_WRITE, BOUNDED_STALENESS -> "mst";
        case STRONG -> "raft";
      };
    }
    partDist
      .setPartitionCount(deploy.getPartitionCount())
      .setReplicaCount(deploy.getReplicaCount())
      .setShardType(shardType)
      .addAllDisabledFns(member.getDisabledFn())
      .addAllStandbyFns(member.getStandbyFn())
      .putAllOptions(generateExtraOptions(cls))
      .putAllOptions(deploy.getOptions())
    ;
    return partDist;
  }

  Map<String, String> generateExtraOptions(OClass cls) {
    var options = new HashMap<String, String>();
    ConsistencyModel consistency = cls.getConstraints().consistency();
    if (consistency==ConsistencyModel.BOUNDED_STALENESS || consistency==ConsistencyModel.READ_YOUR_WRITE) {
      int delayTolerance = cls.getConstraints().delayTolerance();
      if (delayTolerance > 0) {
        int syncInterval = delayTolerance - 800;
        options.put("mst_sync_interval", String.valueOf(syncInterval));
      } else {
        options.put("mst_sync_interval", "5000");
      }
    }
    logger.debug("generate extra options: {}", options);
    return options;
  }
}
