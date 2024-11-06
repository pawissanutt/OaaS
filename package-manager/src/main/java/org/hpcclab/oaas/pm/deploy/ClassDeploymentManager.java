package org.hpcclab.oaas.pm.deploy;

import com.github.f4b6a3.tsid.TsidCreator;
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
import org.hpcclab.oaas.pm.PkgManagerConfig;
import org.hpcclab.oaas.pm.service.CrStateManager;
import org.hpcclab.oaas.pm.service.EnvironmentRegistry;
import org.hpcclab.oaas.pm.service.PackagePublisher;
import org.hpcclab.oaas.proto.DeploymentUnit;
import org.hpcclab.oaas.repository.FunctionRepository;
import org.hpcclab.oaas.repository.PackageDeployer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Pawissanutt
 */
@ApplicationScoped
public class ClassDeploymentManager implements PackageDeployer {
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
    if (deployment.getPartitions().isEmpty()) {
      List<OClassDeployment.PartitionDeployment> partitions = new ArrayList<>();
      for (int i = 0; i < partitionCount; i++) {
        List<OClassDeployment.ReplicaDeployment> replicas = new ArrayList<>();
        for (int j = 0; j < replicaCount; j++) {
          int index = (i + j) % possibleEnv.size();
          replicas.add(new OClassDeployment.ReplicaDeployment()
            .setReplicaId(j)
            .setCrId(TsidCreator.getTsid4096().toLong())
            .setEnv(possibleEnv.get(index)));
        }
        partitions.add(new OClassDeployment.PartitionDeployment()
          .setPartitionId(i)
          .setReplicas(replicas));
      }
      deployment.setPartitions(partitions);
    }
  }



  @Override
  public void deploy(OPackage pkg) {
    for (var deploy : pkg.getDeployments()) {
      var clsOp = pkg.getClasses().stream()
        .filter(c -> c.getKey().equals(deploy.getKey()))
        .findFirst();
      if (clsOp.isPresent()) {
        reassign(deploy);
        var unit = createDeploymentUnit(clsOp.get(), pkg);
        deploy(deploy, unit);
      }
    }
    if (config.kafkaEnabled()) {
      packagePublisher.submitNewPkg(pkg).await().indefinitely();
    }
  }

  void deploy(OClassDeployment deployment, DeploymentUnit unit) {
    for (var partition : deployment.getPartitions()) {
      for (var replica : partition.getReplicas()) {
        DeploymentUnit.Builder builder = unit.toBuilder();
        builder.setCrId(replica.getCrId());
        crStateManager.deploy(replica.getEnv(), builder.build());
      }
    }
    repo.persist(deployment);
  }

  @Override
  public void detach(OClass cls) {
    var deploy = repo.get(cls.getKey());
    for (var partition : deploy.getPartitions()) {
      for (var replica : partition.getReplicas()) {
        crStateManager.undeploy(replica.getEnv(), replica.getCrId());
      }
    }
  }


  DeploymentUnit createDeploymentUnit(OClass cls, OPackage pkg) {
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
      .setCls(protoMapper.toProto(cls))
      .addAllFnList(protoFnList)
      .build();
  }

  public void destroy(String key) {
    throw StdOaasException.notImplemented();
  }
}
