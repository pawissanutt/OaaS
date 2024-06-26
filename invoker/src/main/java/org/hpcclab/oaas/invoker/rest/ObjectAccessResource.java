package org.hpcclab.oaas.invoker.rest;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.hpcclab.oaas.invocation.InvocationReqHandler;
import org.hpcclab.oaas.invocation.task.ContentUrlGenerator;
import org.hpcclab.oaas.invoker.InvokerConfig;
import org.hpcclab.oaas.invoker.InvokerManager;
import org.hpcclab.oaas.invoker.metrics.RequestCounterMap;
import org.hpcclab.oaas.invoker.service.HashAwareInvocationHandler;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.hpcclab.oaas.model.invocation.InvocationRequest;
import org.hpcclab.oaas.model.invocation.InvocationResponse;
import org.hpcclab.oaas.model.object.GOObject;
import org.hpcclab.oaas.model.object.JsonBytes;
import org.hpcclab.oaas.model.proto.DSMap;
import org.hpcclab.oaas.repository.ObjectRepoManager;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @author Pawissanutt
 */
@Path("/api/classes/{cls}/objects/{objId}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ObjectAccessResource {
  final InvokerManager invokerManager;
  final HashAwareInvocationHandler hashAwareInvocationHandler;
  final InvocationReqHandler invocationHandlerService;
  final ObjectRepoManager objectRepoManager;
  final ContentUrlGenerator generator;
  final InvokerConfig conf;
  final RequestCounterMap requestCounterMap;

  public ObjectAccessResource(InvokerManager invokerManager,
                              HashAwareInvocationHandler hashAwareInvocationHandler,
                              InvocationReqHandler invocationHandlerService,
                              ObjectRepoManager objectRepoManager,
                              ContentUrlGenerator generator,
                              InvokerConfig conf,
                              RequestCounterMap requestCounterMap) {
    this.invokerManager = invokerManager;
    this.hashAwareInvocationHandler = hashAwareInvocationHandler;
    this.invocationHandlerService = invocationHandlerService;
    this.objectRepoManager = objectRepoManager;
    this.generator = generator;
    this.conf = conf;
    this.requestCounterMap = requestCounterMap;
  }

  @GET
  public Uni<GOObject> getObj(String cls,
                              String objId) {
    boolean contains = invokerManager.getManagedCls().contains(cls);
    if (contains) {
      return objectRepoManager.getOrCreate(cls).async()
        .getAsync(objId)
        .onItem().ifNull().failWith(() -> StdOaasException.notFoundObject(objId, 404));
    } else {
      return hashAwareInvocationHandler.invoke(InvocationRequest.builder()
          .cls(cls)
          .main(objId)
          .build())
        .map(InvocationResponse::main)
        .onItem().ifNull()
        .failWith(() -> StdOaasException.notFoundObject(objId, 404));
    }
  }

  @GET
  @Path("files/{file:\\w+}")
  public Uni<Response> getObjectFile(String cls,
                                     String objId,
                                     String file) {
    return hashAwareInvocationHandler.invoke(InvocationRequest.builder()
        .cls(cls)
        .main(objId)
        .fb("file")
        .args(DSMap.of("key", file, "pub", "true"))
        .build())
      .map(resp -> {
        String url = Optional.ofNullable(resp.body().getNode())
          .map(o -> o.get(file))
          .map(JsonNode::asText)
          .orElseThrow(() -> StdOaasException.notKeyInObj(file, 404));
        return Response.status(HttpResponseStatus.SEE_OTHER.code())
          .location(URI.create(url))
          .build();
      });
  }

  @GET
  @Path("invokes/{fb}")
  public Uni<InvocationResponse> invoke(String cls,
                                        String objId,
                                        String fb,
                                        @BeanParam InvokeParameters params,
                                        @Context UriInfo uriInfo) {
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
    DSMap args = DSMap.mutable();
    for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
      if (!entry.getKey().startsWith("_"))
        args.put(entry.getKey(), entry.getValue().getFirst());
    }
    var oal = InvocationRequest.builder()
      .cls(cls)
      .main(objId)
      .fb(fb)
      .args(args)
      .partKey(objId)
      .build();
    requestCounterMap.increase(cls, fb);
    if (params.async) {
      return invocationHandlerService.enqueue(oal)
        .map(params::filter);
    }
    return hashAwareInvocationHandler.invoke(oal)
      .map(params::filter);
  }

  @POST
  @Path("invokes/{fb}")
  public Uni<InvocationResponse> invokeWithBody(String cls,
                                                String objId,
                                                String fb,
                                                @Context UriInfo uriInfo,
                                                @BeanParam InvokeParameters params,
                                                ObjectNode body) {
    MultivaluedMap<String, String> queryParameters = uriInfo.getQueryParameters();
    DSMap args = DSMap.mutable();
    for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
      if (!entry.getKey().startsWith("_"))
        args.put(entry.getKey(), entry.getValue().getFirst());
    }
    InvocationRequest request = InvocationRequest.builder()
      .cls(cls)
      .main(objId)
      .fb(fb)
      .args(args)
      .body(new JsonBytes(body))
      .build();
    requestCounterMap.increase(cls, fb);
    if (params.async) {
      return invocationHandlerService.enqueue(request)
        .map(params::filter);
    }
    return hashAwareInvocationHandler.invoke(request)
      .map(params::filter);
  }
}
