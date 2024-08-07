package org.hpcclab.oaas.invocation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.mutiny.core.MultiMap;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.Router;
import io.vertx.mutiny.ext.web.RoutingContext;
import org.hpcclab.oaas.invocation.InvocationReqHandler;
import org.hpcclab.oaas.invocation.LocationAwareInvocationForwarder;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.hpcclab.oaas.model.invocation.InvocationRequest;
import org.hpcclab.oaas.model.invocation.InvocationResponse;
import org.hpcclab.oaas.model.object.GOObject;
import org.hpcclab.oaas.model.object.JsonBytes;
import org.hpcclab.oaas.model.proto.DSMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author Pawissanutt
 */
public class VertxInvocationRoutes implements VertxRouteService{
  final LocationAwareInvocationForwarder invocationForwarder;
  final InvocationReqHandler invocationReqHandler;
  final ObjectMapper mapper;

  public VertxInvocationRoutes(LocationAwareInvocationForwarder invocationForwarder,
                               InvocationReqHandler invocationReqHandler,
                               ObjectMapper mapper) {
    this.invocationForwarder = invocationForwarder;
    this.invocationReqHandler = invocationReqHandler;
    this.mapper = mapper;
  }

  @Override
  public void mountRouter(Router router) {
    router.route().failureHandler(this::handleException);
    router.route("/classes/:cls/invokes/:fb")
      .method(HttpMethod.GET)
      .method(HttpMethod.POST)
      .respond(this::invoke);
    router.route("/classes/:cls/objects/:obj")
      .method(HttpMethod.GET)
      .respond(this::getObject);
    router.route("/classes/:cls/objects/:obj/files/:file")
      .method(HttpMethod.GET)
      .respond(this::getObjectFile);
    router.route("/classes/:cls/objects/:obj/invokes/:fb")
      .method(HttpMethod.GET)
      .method(HttpMethod.POST)
      .respond(this::invoke);
  }


  Uni<InvocationResponse> invoke(RoutingContext context) {
    String cls = context.pathParam("cls");
    String main = context.pathParam("obj");
    String fb = context.pathParam("fb");
    var params = InvokeParameters.create(context);
    JsonBytes body = null;
    Buffer buffer = context.body().buffer();
    if (buffer != null && buffer.length() > 0) {
      try {
        body = mapper.readValue(buffer.getBytes(), JsonBytes.class);
      } catch (IOException e) {
        return Uni.createFrom().failure(new StdOaasException("Failed to parse body as JSON", e, false, 400));
      }
    }
    MultiMap queryParameters = context.queryParams();
    Map<String, String> args = new HashMap<>();
    queryParameters.entries()
      .stream()
      .filter(e -> !e.getValue().startsWith("_"))
      .forEach(e -> args.put(e.getKey(), e.getValue()));
    InvocationRequest oal = InvocationRequest.builder()
      .cls(cls)
      .main(main)
      .fb(fb)
      .args(args)
      .body(body)
      .build();
    if (params.async()) {
      return invocationReqHandler.enqueue(oal);
    }
    return invocationForwarder.invoke(oal)
      .map(params::filter);
  }

  public Uni<GOObject> getObject(RoutingContext context) {
    String cls = context.pathParam("cls");
    String main = context.pathParam("obj");
    return invocationForwarder.invoke(InvocationRequest.builder()
        .cls(cls)
        .main(main)
        .build())
      .map(InvocationResponse::main)
      .onItem().ifNull()
      .failWith(() -> StdOaasException.notFoundObject(main, 404));
  }

  public Uni<Void> getObjectFile(RoutingContext context) {
    String cls = context.pathParam("cls");
    String main = context.pathParam("obj");
    String file = context.pathParam("file");
    return invocationForwarder.invoke(InvocationRequest.builder()
        .cls(cls)
        .main(main)
        .fb("file")
        .args(DSMap.of("key", file, "pub", "true"))
        .build())
      .flatMap(resp -> {
        String url = Optional.ofNullable(resp.body().getNode())
          .map(o -> o.get(file))
          .map(JsonNode::asText)
          .orElseThrow(() -> StdOaasException.notKeyInObj(file, 404));
        return context.response()
          .setStatusCode(HttpResponseStatus.SEE_OTHER.code())
          .putHeader(HttpHeaders.LOCATION, url)
          .end();
      });
  }
}
