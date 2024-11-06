package org.hpcclab.oaas.pm.rest;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.hpcclab.oaas.model.Pagination;
import org.hpcclab.oaas.model.cr.OClassRuntime;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.hpcclab.oaas.pm.service.CrStateManager;
import org.hpcclab.oaas.pm.service.EnvironmentRegistry;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/api/class-runtimes")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CrResource {
  final CrStateManager stateManager;
  final EnvironmentRegistry registry;

  @Inject
  public CrResource(CrStateManager stateManager,
                    EnvironmentRegistry registry) {
    this.stateManager = stateManager;
    this.registry = registry;

  }

  @GET
  public Uni<Pagination<OClassRuntime>> listOrbit(@RestQuery Integer limit,
                                                  @RestQuery Integer offset) {
    return stateManager.getCrRepo()
      .getQueryService()
      .paginationAsync(offset==null ? 0:offset, limit==null ? 20:limit);
  }

  @Path("{id}")
  @DELETE
  @RunOnVirtualThread
  public void delete(String id) {
    var cr = stateManager.getAsProto(id).await().indefinitely();
    if (cr!=null) {
      var env = cr.getEnv();
      var crm = registry.getCrmStub(env);
      var res = crm.destroy(cr);
      stateManager.getCrRepo().delete(id);
      if (!res.getSuccess())
        throw StdOaasException.format("Error deleting cr %s", id);
    } else {
      throw new NotFoundException();
    }
  }
}
