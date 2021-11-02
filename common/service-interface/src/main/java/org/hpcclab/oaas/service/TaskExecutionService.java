package org.hpcclab.oaas.service;

import io.smallrye.mutiny.Uni;
import org.hpcclab.oaas.model.TaskExecRequest;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/task-exec/")
public interface TaskExecutionService {
  @POST
  Uni<Void> request(TaskExecRequest request);
}
