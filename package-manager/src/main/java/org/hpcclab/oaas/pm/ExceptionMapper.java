package org.hpcclab.oaas.pm;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

@ApplicationScoped
public class ExceptionMapper {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionMapper.class);

  @ServerExceptionMapper(StatusRuntimeException.class)
  public Response exceptionMapper(StatusRuntimeException statusRuntimeException) {
    Response.Status status = Response.Status.INTERNAL_SERVER_ERROR;
    if (statusRuntimeException.getStatus().getCode()==Status.Code.UNAVAILABLE)
      status = Response.Status.SERVICE_UNAVAILABLE;
    if (statusRuntimeException.getStatus().getCode()==Status.Code.RESOURCE_EXHAUSTED)
      status = Response.Status.TOO_MANY_REQUESTS;
    if (statusRuntimeException.getStatus().getCode()==Status.Code.INVALID_ARGUMENT)
      status = Response.Status.BAD_REQUEST;
    if (statusRuntimeException.getStatus().getCode()==Status.Code.UNIMPLEMENTED)
      status = Response.Status.NOT_IMPLEMENTED;
    if (statusRuntimeException.getStatus().getCode()==Status.Code.UNAUTHENTICATED)
      status = Response.Status.UNAUTHORIZED;
    if (LOGGER.isWarnEnabled() || status==Response.Status.INTERNAL_SERVER_ERROR) {
      LOGGER.warn("mapping StatusRuntimeException: {}", statusRuntimeException.getMessage());
    } else if (LOGGER.isDebugEnabled())
      LOGGER.debug("mapping StatusRuntimeException({})", status);
    return Response.status(status)
      .entity(new JsonObject()
        .put("msg", statusRuntimeException.getMessage()))
      .build();
  }

  @ServerExceptionMapper(IllegalArgumentException.class)
  public Response exceptionMapper(IllegalArgumentException illegalArgumentException) {
    return Response.status(404)
      .entity(new JsonObject()
        .put("msg", illegalArgumentException.getMessage()))
      .build();
  }

  @ServerExceptionMapper(WebApplicationException.class)
  public Response exceptionMapper(WebApplicationException webApplicationException) {
    return Response.fromResponse(webApplicationException.getResponse())
      .entity(new JsonObject()
        .put("msg", webApplicationException.getMessage()))
      .build();
  }

  @ServerExceptionMapper(StdOaasException.class)
  public Response exceptionMapper(StdOaasException exception) {
    return Response.status(exception.getCode())
      .entity(new JsonObject()
        .put("msg", exception.getMessage()))
      .build();
  }

  @ServerExceptionMapper(JsonMappingException.class)
  public Response exceptionMapper(JsonMappingException jsonMappingException) {
    return Response.status(400)
      .entity(new JsonObject()
        .put("msg", jsonMappingException.getMessage()))
      .build();
  }

  @ServerExceptionMapper(JsonParseException.class)
  public Response exceptionMapper(JsonParseException jsonParseException) {
    return Response.status(400)
      .entity(new JsonObject()
        .put("msg", jsonParseException.getMessage()))
      .build();
  }

  @ServerExceptionMapper(ConstraintViolationException.class)
  public Response exceptionMapper(ConstraintViolationException exception) {
    return Response.status(400)
      .entity(new JsonObject()
        .put("msg", "Message body is not valid")
        .put("violations", exception.getConstraintViolations()
          .stream()
          .map(cv -> cv.getPropertyPath().toString() + " " + cv.getMessage())
          .collect(Collectors.toList())
        ))
      .build();
  }
}
