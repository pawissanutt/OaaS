package org.hpcclab.oaas.pm.cdi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.hpcclab.oaas.arango.AutoRepoBuilder;
import org.hpcclab.oaas.arango.RepoFactory;
import org.hpcclab.oaas.arango.repo.ArgClsRepository;
import org.hpcclab.oaas.arango.repo.ArgFunctionRepository;
import org.hpcclab.oaas.arango.repo.GenericArgRepository;
import org.hpcclab.oaas.invocation.service.VertxPackageRoutes;
import org.hpcclab.oaas.mapper.ProtoMapper;
import org.hpcclab.oaas.model.cr.CrHash;
import org.hpcclab.oaas.model.cr.OClassRuntime;
import org.hpcclab.oaas.repository.*;
import org.hpcclab.oaas.repository.id.IdGenerator;
import org.hpcclab.oaas.repository.id.TsidGenerator;
import org.hpcclab.oaas.repository.store.DatastoreConfRegistry;

@ApplicationScoped
public class RepoProducer {
  @Produces
  @ApplicationScoped
  ArgClsRepository clsRepository() {
    return AutoRepoBuilder.clsRepository();
  }

  @Produces
  @ApplicationScoped
  ArgFunctionRepository funcRepository() {
    return AutoRepoBuilder.funcRepository();
  }

  @Produces
  @ApplicationScoped
  ClassResolver classResolver(ClassRepository classRepository) {
    return new ClassResolver(classRepository);
  }

  @Produces
  @Singleton
  IdGenerator idGenerator() {
    return new TsidGenerator();
  }

  @Produces
  @ApplicationScoped
  PackageValidator packageValidator(FunctionRepository functionRepository) {
    return new PackageValidator(functionRepository);
  }

  @Produces
  @ApplicationScoped
  VertxPackageRoutes vertxPackageService(ClassRepository classRepo,
                                         FunctionRepository funcRepo,
                                         PackageValidator validator,
                                         ClassResolver classResolver,
                                         ProtoMapper protoMapper,
                                         PackageDeployer packageDeployer) {
    return new VertxPackageRoutes(
      classRepo,
      funcRepo,
      validator,
      classResolver,
      protoMapper,
      packageDeployer
    );
  }

  @Produces
  @ApplicationScoped
  GenericArgRepository<OClassRuntime> crRepo() {
    DatastoreConfRegistry registry = DatastoreConfRegistry.getDefault();
    var fac = new RepoFactory(registry.getConfMap().get("PKG"));
    var crRepo = fac.createGenericRepo(OClassRuntime.class, OClassRuntime::getKey, "cr");
    crRepo.createIfNotExist();
    return crRepo;
  }

  @Produces
  @ApplicationScoped
  GenericArgRepository<CrHash> hashRepo() {
    DatastoreConfRegistry registry = DatastoreConfRegistry.getDefault();
    var fac = new RepoFactory(registry.getConfMap().get("PKG"));
    var hashRepo = fac.createGenericRepo(CrHash.class, CrHash::getKey, "crHash");
    hashRepo.createIfNotExist();
    return hashRepo;
  }
}
