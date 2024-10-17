package org.hpcclab.oaas.pm.rpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.hpcclab.oaas.arango.repo.GenericArgRepository;
import org.hpcclab.oaas.model.Pagination;
import org.hpcclab.oaas.model.cr.OClassRuntime;
import org.hpcclab.oaas.model.cr.OcrRouting;
import org.hpcclab.oaas.pm.service.CrStateManager;
import org.hpcclab.oaas.proto.*;

/**
 * @author Pawissanutt
 */
@GrpcService
public class RoutingServiceImpl implements RoutingService {

  CrStateManager crStateManager;
  GenericArgRepository<OClassRuntime> crRepo;

  @Inject
  public RoutingServiceImpl(CrStateManager crStateManager,
                            GenericArgRepository<OClassRuntime> crRepo) {
    this.crStateManager = crStateManager;
    this.crRepo = crRepo;
  }

  public RoutingServiceImpl(CrStateManager crStateManager) {
    this.crStateManager = crStateManager;
  }

  @Override
  public Uni<ClsRoutingTable> getClsRouting(ClsRoutingRequest request) {
    return
      crRepo.getQueryService().paginationAsync(0, 1000)
        .map(this::createTable);
  }

  private ClsRoutingTable createTable(Pagination<OClassRuntime> page) {
    ClsRoutingTable.Builder builder = ClsRoutingTable.newBuilder();
    for (OClassRuntime ocr : page.items()) {
      builder.addClss(createRouting(ocr));
    }
    return builder.build();
  }


  private ClsRouting createRouting(OClassRuntime ocr) {
    OcrRouting routing = ocr.routing();

    ClsRouting.Builder builder = ClsRouting.newBuilder();
    for (OcrRouting.PartitionRouting partition : routing.partitions()) {
      PartitionRouting.Builder partitionBuilder = PartitionRouting.newBuilder();
      for (var entry : partition.functions().entrySet()) {
        partitionBuilder.putFuncs(
          entry.getKey(),
          FuncRouting.newBuilder().setUri(entry.getValue().url()).build()
        );
      }
      builder.addRouting(partitionBuilder.build());
    }
    return builder
      .build();
  }

  @Override
  public Multi<ClsRouting> watchClsRouting(ClsRoutingRequest request) {
    return crStateManager.getCrBroadcaster()
      .map(this::createRouting);
  }
}
