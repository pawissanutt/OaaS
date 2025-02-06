package org.hpcclab.oaas.pm.rpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hpcclab.oaas.pm.service.CrStateManager;
import org.hpcclab.oaas.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GrpcService
public class CrStateServiceImpl implements InternalCrStateService, CrStateService {
  private static final Logger logger = LoggerFactory.getLogger( CrStateServiceImpl.class );
  CrStateManager stateManager;
  @Inject
  public CrStateServiceImpl(CrStateManager stateManager) {
    this.stateManager = stateManager;
  }

  @Override
  @RunOnVirtualThread
  public Uni<ProtoCr> get(SingleKeyQuery request) {
    return stateManager.getAsProto(request.getKey());
  }

  @Override
  public Multi<ProtoCr> list(PaginateQuery request) {
    return stateManager.listCr(request);
  }

  @Override
  public Multi<ProtoCr> selectFromEnv(EnvSelector request) {
    return stateManager.selectFromEnv(request)
      .onFailure().invoke(e -> logger.error("selectFromEnv", e));
  }

  @Override
  public Uni<OprcResponse> updateCr(ProtoCr request) {
    return stateManager.updateCr(request);
  }

  @Override
  public Uni<ProtoCrHash> updateHash(ProtoCrHash request) {
    return stateManager.updateCrHash(request);
  }

  @Override
  public Uni<ProtoCrHash> getHash(SingleKeyQuery request) {
    return stateManager.getHash(request.getKey());
  }

  @Override
  public Multi<ProtoCrHash> listHash(PaginateQuery request) {
    return stateManager.listHash(request);
  }
}
