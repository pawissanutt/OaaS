package org.hpcclab.oaas.task.handler;

import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hpcclab.oaas.model.ErrorMessage;
import org.hpcclab.oaas.model.task.TaskCompletion;
import org.hpcclab.oaas.model.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/")
@RequestScoped

public class CompletionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(CompletionHandler.class);

  @Inject
  RoutingContext ctx;
//  @Channel("task-completions")
//  MutinyEmitter<TaskCompletion> tasksCompletionEmitter;
//  @ConfigProperty(name = "quarkus.opentelemetry.enabled")
//  boolean openTelemetryEnabled;
  @Inject
  @RestClient
  CompletionSubmissionService submissionService;

  @POST
  public Uni<Response> handle(String body) {
    var headers = ctx.request().headers();
    var ceType = headers.get("ce-type");
    return switch (ceType) {
      case "dev.knative.kafka.event", "oaas.task" -> handleDeadLetter(body)
        .map(ignore -> Response.ok().build());
      case "oaas.task.result" -> handleResult(body)
        .map(ignore -> Response.ok().build());
      default -> Uni.createFrom()
        .item(Response.status(404)
          .entity(new ErrorMessage("Can not handle type: " + ceType)).build());

    };
  }

  public Uni<Void> handleDeadLetter(String body) {
    var headers = ctx.request().headers();
    var ceId = headers.get("ce-id");
    LOGGER.info("received dead letter: {}", ceId);
    var error = headers.get("ce-knativeerrordata");
//    var func = headers.get("ce-function");
    var taskCompletion = new TaskCompletion()
      .setId(ceId)
      .setSuccess(false)
//      .setStatus(TaskStatus.FAILED)
//      .setFunctionName(func)
      .setDebugLog(error);
    return sendUni(taskCompletion);
  }


  public Uni<Void> handleResult(String body) {
    var headers = ctx.request().headers();
    var ceId = headers.get("ce-id");
    LOGGER.info("received task result: {}", ceId);
    var objectId = ceId.split("/")[0];
    var succeededHeader = headers.get("ce-tasksucceeded");
    var succeeded = succeededHeader==null || Boolean.parseBoolean(succeededHeader);
    var taskCompletion = new TaskCompletion()
      .setId(objectId)
      .setSuccess(succeeded)
      .setDebugLog(body);
    return sendUni(taskCompletion);
  }

  Uni<Void> sendUni(TaskCompletion taskCompletion) {
    return submissionService.submit(List.of(taskCompletion));
  }
}
