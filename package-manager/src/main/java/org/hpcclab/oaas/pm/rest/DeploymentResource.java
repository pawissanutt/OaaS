package org.hpcclab.oaas.pm.rest;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.hpcclab.oaas.model.Pagination;
import org.hpcclab.oaas.model.pkg.OClassDeployment;
import org.hpcclab.oaas.pm.deploy.ClassDeploymentManager;
import org.jboss.resteasy.reactive.RestQuery;

@Path("/api/deployments")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeploymentResource {
  final ClassDeploymentManager deploymentManager;
  @Inject
  public DeploymentResource(ClassDeploymentManager deploymentManager) {
    this.deploymentManager = deploymentManager;
  }

  @GET
  public Uni<Pagination<OClassDeployment>> list(@RestQuery Integer limit,
                                                @RestQuery Integer offset) {
    return deploymentManager.getRepo()
      .getQueryService()
      .paginationAsync(offset==null ? 0:offset, limit==null ? 20:limit);
  }

  @Path("{id}")
  @DELETE
  @RunOnVirtualThread
  public void delete(String key) {
    deploymentManager.destroy(key);
  }
}
