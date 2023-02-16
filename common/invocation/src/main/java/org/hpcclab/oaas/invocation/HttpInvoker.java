package org.hpcclab.oaas.invocation;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import io.vertx.mutiny.core.MultiMap;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.hpcclab.oaas.invocation.config.HttpInvokerConfig;
import org.hpcclab.oaas.model.exception.InvocationException;
import org.hpcclab.oaas.model.task.TaskContext;
import org.hpcclab.oaas.model.task.TaskCompletion;
import org.hpcclab.oaas.model.task.TaskIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

@ApplicationScoped
public class HttpInvoker implements SyncInvoker {
  private static final Logger logger = LoggerFactory.getLogger(HttpInvoker.class);
  WebClient webClient;
  HttpInvokerConfig config;

  @Inject
  public HttpInvoker(WebClient webClient,
                     HttpInvokerConfig config) {
    this.webClient = webClient;
    this.config = config;
  }

  @Override
  public Uni<TaskCompletion> invoke(InvokingDetail<?> invokingDetail) {
    if (logger.isDebugEnabled())
      logger.debug("invoke {}", invokingDetail.getId());
    var content = invokingDetail.getContent();
    Buffer contentBuffer;
    if (content instanceof Buffer buffer) {
      contentBuffer = buffer;
    } else if (content instanceof io.vertx.core.buffer.Buffer buffer) {
      contentBuffer = Buffer.newInstance(buffer);
    } else {
      contentBuffer = Buffer.newInstance(Json.encodeToBuffer(content));
    }
    return webClient.postAbs(invokingDetail.getFuncUrl())
      .putHeaders(createHeader(invokingDetail))
      .timeout(config.getTimout())
      .sendBuffer(contentBuffer)
      .map(resp -> this.handleResp(invokingDetail, resp))
      .onFailure()
      .transform(InvocationException::connectionErr)
//      .recoverWithItem(e -> TaskCompletion.error(
//        TaskIdentity.decode(invokingDetail.getId()),
//        "Fail to perform invocation: " + e.getMessage(),
//        invokingDetail.getSmtTs(),
//        System.currentTimeMillis())
//      )
      ;
  }

  protected MultiMap createHeader(InvokingDetail<?> detail) {
    return MultiMap.caseInsensitiveMultiMap()
      .add("ce-type", config.getCeType())
      .add("ce-function", detail.getFuncName())
      .add("ce-id", detail.getId())
      .add("ce-source", config.getAppName())
      .add("ce-specversion", "1.0")
      .add("content-type", "application/json");
  }

  TaskCompletion handleResp(InvokingDetail<?> detail, HttpResponse<Buffer> resp) {
    if (resp.statusCode()==200)
      return TaskDecoder.tryDecode(detail.getId(), resp.bodyAsBuffer().getDelegate())
        .setSmtTs(detail.getSmtTs());
    else
      return TaskCompletion.error(
        TaskIdentity.decode(detail.getId()),
        "Fail to perform invocation: function return not 200 code (%s)"
          .formatted(resp.statusCode()),
        System.currentTimeMillis(),
        detail.smtTs
      );
  }

}