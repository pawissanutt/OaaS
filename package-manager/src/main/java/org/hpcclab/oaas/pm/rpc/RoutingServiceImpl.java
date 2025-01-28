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

import java.util.List;

/**
 * @author Pawissanutt
 */
@GrpcService
@Deprecated
public class RoutingServiceImpl implements RoutingService {

  CrStateManager crStateManager;
  GenericArgRepository<OClassRuntime> crRepo;

  @Inject
  public RoutingServiceImpl(CrStateManager crStateManager,
                            GenericArgRepository<OClassRuntime> crRepo) {
    this.crStateManager = crStateManager;
    this.crRepo = crRepo;
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
    var routing = List.of(ocr.routing());
    if (ocr.attachedCls() == null || ocr.attachedCls().isEmpty()) {
      return ClsRouting.newBuilder().build();
    }
    ClsRouting.Builder builder = ClsRouting.newBuilder()
      .setName(ocr.attachedCls().getFirst().getKey());
    for (OcrRouting.PartitionRouting partition : routing) {
      PartitionRouting.Builder partitionBuilder = PartitionRouting.newBuilder();
      for (var entry : partition.functions().entrySet()) {
        partitionBuilder.putFunctions(
          entry.getKey(),
          FuncRouting.newBuilder().setUrl(entry.getValue().url()).build()
        );
      }
      builder
        .addRouting(partitionBuilder.build());
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
