package org.hpcclab.oaas.invocation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.hpcclab.oaas.invocation.controller.InvocationLog;
import org.hpcclab.oaas.invocation.state.StateOperation;
import org.hpcclab.oaas.model.invocation.InvocationChain;
import org.hpcclab.oaas.model.invocation.InvocationRequest;
import org.hpcclab.oaas.model.invocation.InvocationResponse;
import org.hpcclab.oaas.model.object.GOObject;
import org.hpcclab.oaas.model.object.JsonBytes;
import org.hpcclab.oaas.model.proto.DSMap;
import org.hpcclab.oaas.model.task.OTaskCompletion;

import java.util.List;
import java.util.Map;

/**
 * @author Pawissanutt
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvocationCtx {
  InvocationRequest request;
  GOObject output;
  GOObject main;
  Map<String, GOObject> mainRefs;
  Map<String, String> args = Map.of();
  boolean immutable;
  List<String> macroInvIds = Lists.mutable.empty();
  Map<String, String> macroIds = Maps.mutable.empty();
  OTaskCompletion completion;
  JsonBytes respBody;
  List<StateOperation> stateOperations = List.of();
  InvocationLog log;
  List<InvocationChain> chains = List.of();
  long mqOffset = -1;
  long initTime = -1;

  public InvocationLog initLog() {
    if (log!=null)
      return log;

    log = new InvocationLog();
    if (request!=null) {
      log.setKey(request.invId());
      log.setOutId(output!=null ? output.getKey():null);
    } else {
      log.setKey(getOutput().getKey());
      log.setOutId(getOutput().getKey());
    }
    log.setMain(main!=null ? getMain().getKey():null);
    log.setFb(request!=null ? request.fb():null);
    log.setArgs(DSMap.copy(getArgs()));
    log.setCls(request.cls());
    return log;
  }

  public InvocationResponse.InvocationResponseBuilder createResponse() {
    return InvocationResponse.builder()
      .invId(request.invId())
      .main(getMain())
      .output(getOutput())
      .fb(request.fb())
      .status(log==null ? null:log.getStatus())
      .body(respBody)
      .stats(log==null ? null:log.extractStats())
      .macroIds(macroIds)
      .macroInvIds(macroInvIds)
      ;
  }

  public InvocationCtx setRespBody(ObjectNode node) {
    this.respBody = new JsonBytes(node);
    return this;
  }

  public InvocationCtx setRespBody(JsonBytes jb) {
    this.respBody = jb;
    return this;
  }
}
