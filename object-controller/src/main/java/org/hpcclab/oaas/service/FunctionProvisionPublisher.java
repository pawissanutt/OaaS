package org.hpcclab.oaas.service;


import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.hpcclab.oaas.entity.function.OaasFunction;
import org.hpcclab.oaas.mapper.OaasMapper;
import org.hpcclab.oaas.model.function.OaasFunctionDto;
import org.hpcclab.oaas.model.task.OaasTask;

import io.smallrye.reactive.messaging.kafka.Record;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.stream.Stream;

@ApplicationScoped
public class FunctionProvisionPublisher {
  @Channel("provisions")
  MutinyEmitter<Record<String, OaasFunctionDto>> provisionEmitter;
  @Inject
  OaasMapper mapper;

  public Uni<Void> submitNewFunction(OaasFunction function) {
    var provision = function.getProvision();
    if (provision == null || provision.getKnative() == null) {
      return Uni.createFrom().nullItem();
    }
    return provisionEmitter
      .send(Record.of(function.getName(), mapper.toFunc(function)));
  }

  public Uni<Void> submitDelete(String funcName) {
    return provisionEmitter
      .send(Record.of(funcName, null));
  }

  public Uni<Void> submitNewFunction(Stream<OaasFunction> functions) {
    return Multi.createFrom().items(functions)
      .onItem()
      .transformToUniAndConcatenate(this::submitNewFunction)
      .collect().last();
  }
}