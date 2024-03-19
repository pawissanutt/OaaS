package org.hpcclab.oaas.invoker.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.runtime.Startup;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.hpcclab.oaas.invocation.InvocationReqHandler;
import org.hpcclab.oaas.invocation.task.ContentUrlGenerator;
import org.hpcclab.oaas.invoker.InvokerConfig;
import org.hpcclab.oaas.invoker.service.HashAwareInvocationHandler;
import org.hpcclab.oaas.model.Views;
import org.hpcclab.oaas.model.data.AccessLevel;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.hpcclab.oaas.model.invocation.InvocationResponse;
import org.hpcclab.oaas.model.invocation.InvocationStatus;
import org.hpcclab.oaas.model.oal.ObjectAccessLanguage;
import org.hpcclab.oaas.model.object.OObject;
import org.hpcclab.oaas.repository.ObjectRepoManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

@Path("/oal")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Startup
public class OalResource {
  private static final Logger LOGGER = LoggerFactory.getLogger(OalResource.class);

  final ObjectRepoManager objectRepoManager;
  final ContentUrlGenerator contentUrlGenerator;
  final InvocationReqHandler invocationHandlerService;
  final HashAwareInvocationHandler hashAwareInvocationHandler;
  final InvokerConfig conf;

  @Inject
  public OalResource(ObjectRepoManager objectRepoManager,
                     ContentUrlGenerator contentUrlGenerator,
                     InvocationReqHandler invocationHandlerService,
                     HashAwareInvocationHandler hashAwareInvocationHandler,
                     InvokerConfig conf) {
    this.objectRepoManager = objectRepoManager;
    this.contentUrlGenerator = contentUrlGenerator;
    this.invocationHandlerService = invocationHandlerService;
    this.hashAwareInvocationHandler = hashAwareInvocationHandler;
    this.conf = conf;
  }

  @POST
  @JsonView(Views.Public.class)
  @Operation(hidden = true)
  public Uni<InvocationResponse> getObjectWithPost(ObjectAccessLanguage oal,
                                                   @QueryParam("async") Boolean async) {
    if (oal==null || oal.getCls()==null)
      return Uni.createFrom().failure(BadRequestException::new);

    if (oal.getFb()!=null) {
      return selectAndInvoke(oal, async);
    } else {
      return objectRepoManager.getOrCreate(oal.getCls())
        .async().getAsync(oal.getMain())
        .onItem().ifNull()
        .failWith(() -> StdOaasException.notFoundObject(oal.getMain(), 404))
        .map(obj -> InvocationResponse.builder()
          .main(obj)
          .build());
    }
  }

  @GET
  @Path("{oal:.+}")
  @JsonView(Views.Public.class)
  @Operation(hidden = true)
  public Uni<InvocationResponse> getObject(@PathParam("oal") String oal,
                                           @QueryParam("async") Boolean async) {
    var oaeObj = ObjectAccessLanguage.parse(oal);
    LOGGER.debug("Receive OAL getObject '{}'", oaeObj);
    return getObjectWithPost(oaeObj, async);
  }

  @POST
  @Path("-/{filePath::\\w+}")
  @JsonView(Views.Public.class)
  @Operation(hidden = true)
  public Uni<Response> execAndGetContentPost(@PathParam("filePath") String filePath,
                                             @QueryParam("async") Boolean async,
                                             ObjectAccessLanguage oal) {
    if (oal==null)
      return Uni.createFrom().failure(BadRequestException::new);
    if (oal.getCls()==null)
      return Uni.createFrom().failure(BadRequestException::new);
    if (oal.getFb()!=null) {
      return selectAndInvoke(oal, async)
        .map(res -> createResponse(res, filePath));
    } else {
      return objectRepoManager
        .getOrCreate(oal.getCls()).async()
        .getAsync(oal.getMain())
        .onItem().ifNull()
        .failWith(() -> StdOaasException.notFoundObject(oal.getMain(), 404))
        .map(obj -> createResponse(obj, filePath));
    }
  }


  @GET
  @JsonView(Views.Public.class)
  @Path("{oal:.+}/{filePath:\\w+}")
  @Operation(hidden = true)
  public Uni<Response> execAndGetContent(@PathParam("oal") String oal,
                                         @PathParam("filePath") String filePath,
                                         @QueryParam("async") Boolean async) {
    var oalObj = ObjectAccessLanguage.parse(oal);
    LOGGER.debug("Receive OAL getContent '{}' '{}'", oalObj, filePath);
    return execAndGetContentPost(filePath, async, oalObj);
  }

  public Uni<InvocationResponse> selectAndInvoke(ObjectAccessLanguage oal, Boolean async) {
    if (async!=null && async) {
      return invocationHandlerService.enqueue(oal);
    } else {
      return hashAwareInvocationHandler.invoke(oal);
    }
  }

  public Response createResponse(InvocationResponse invocationResponse,
                                 String filePath) {
    return createResponse(invocationResponse, filePath, HttpResponseStatus.SEE_OTHER.code());
  }


  public Response createResponse(OObject object,
                                 String filePath) {
    return createResponse(object, filePath, HttpResponseStatus.SEE_OTHER.code());
  }

  public Response createResponse(InvocationResponse response,
                                 String filePath,
                                 int redirectCode) {
    var obj = response.output()!=null ? response.output():response.main();
    var ts = response.status();
    if (ts==InvocationStatus.DOING) {
      return Response.status(HttpResponseStatus.GATEWAY_TIMEOUT.code())
        .build();
    }
    if (ts.isFailed()) {
      return Response.status(HttpResponseStatus.FAILED_DEPENDENCY.code()).build();
    }
    var oUrl = obj.getState().getOverrideUrls();
    var replaced = oUrl!=null ? oUrl.get(filePath):null;
    if (replaced!=null)
      return Response.status(redirectCode)
        .location(URI.create(replaced))
        .build();
    var fileUrl = contentUrlGenerator.generateUrl(obj, filePath, AccessLevel.UNIDENTIFIED, conf.respPubS3());
    return Response.status(redirectCode)
      .location(URI.create(fileUrl))
      .build();
  }

  public Response createResponse(OObject object,
                                 String filePath,
                                 int redirectCode) {
    if (object==null) return Response.status(404).build();
    var oUrl = object.getState().getOverrideUrls();
    var replaced = oUrl!=null ? oUrl.get(filePath):null;
    if (replaced!=null)
      return Response.status(redirectCode)
        .location(URI.create(replaced))
        .build();
    var fileUrl = contentUrlGenerator.generateUrl(object, filePath, AccessLevel.UNIDENTIFIED, conf.respPubS3());
    return Response.status(redirectCode)
      .location(URI.create(fileUrl))
      .build();
  }
}
