package org.hpcclab.oaas.invoker.verticle;

import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.mutiny.kafka.client.consumer.KafkaConsumerRecord;
import org.hpcclab.oaas.invocation.ContextLoader;
import org.hpcclab.oaas.invocation.InvocationExecutor;
import org.hpcclab.oaas.invocation.SyncInvoker;
import org.hpcclab.oaas.invocation.applier.UnifiedFunctionRouter;
import org.hpcclab.oaas.invoker.InvokerConfig;
import org.hpcclab.oaas.model.exception.InvocationException;
import org.hpcclab.oaas.model.function.FunctionExecContext;
import org.hpcclab.oaas.model.invocation.InvocationRequest;
import org.hpcclab.oaas.repository.FunctionRepository;
import org.hpcclab.oaas.repository.event.ObjectCompletionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import java.time.Duration;

@Dependent
public class OrderedInvocationHandlerVerticle extends AbstractOrderedRecordVerticle {
  private static final Logger LOGGER = LoggerFactory.getLogger(OrderedInvocationHandlerVerticle.class);
  final SyncInvoker invoker;
  final FunctionRepository funcRepo;
  final InvocationExecutor invocationExecutor;
  final ObjectCompletionPublisher objCompPublisher;
  final ContextLoader loader;
  final UnifiedFunctionRouter router;

  @Inject
  public OrderedInvocationHandlerVerticle(SyncInvoker invoker,
                                          FunctionRepository funcRepo,
                                          InvocationExecutor graphExecutor,
                                          ObjectCompletionPublisher objCompPublisher,
                                          InvokerConfig invokerConfig,
                                          UnifiedFunctionRouter router,
                                          ContextLoader loader) {
    super(invokerConfig.invokeConcurrency());
    this.invoker = invoker;
    this.funcRepo = funcRepo;
    this.invocationExecutor = graphExecutor;
    this.objCompPublisher = objCompPublisher;
    this.router = router;
    this.loader = loader;
  }

  @Override
  public void handleRecord(KafkaConsumerRecord<String, Buffer> kafkaRecord) {
    var request = Json.decodeValue(kafkaRecord.value(), InvocationRequest.class);
    if (LOGGER.isDebugEnabled()) {
      logLatency(kafkaRecord);
    }
    if (request.macro()) {
      generateMacro(kafkaRecord, request);
    } else {
      invokeTask(kafkaRecord, request);
    }
  }

  private void generateMacro(KafkaConsumerRecord<String, Buffer> kafkaRecord, InvocationRequest request) {
    loader.loadCtxAsync(request)
      .flatMap(router::apply)
      .flatMap(invocationExecutor::asyncSubmit)
      .onFailure().retry()
      .withBackOff(Duration.ofMillis(100))
      .atMost(3)
      .subscribe()
      .with(
        ctx -> next(kafkaRecord),
        error -> {
          LOGGER.error("Unexpected error on invoker ", error);
          next(kafkaRecord);
        }
      );
  }

  private void invokeTask(KafkaConsumerRecord<String, Buffer> kafkaRecord, InvocationRequest request) {
    if (LOGGER.isDebugEnabled())
      LOGGER.debug("invokeTask {}", request);
    loader.loadCtxAsync(request)
      .flatMap(ctx -> {
        if (detectDuplication(kafkaRecord, ctx)) {
          LOGGER.warn("detect duplication {} {}", ctx.getRequest().target(), ctx.getRequest().outId());
          return Uni.createFrom().nullItem();
        }
        return router.apply(ctx)
          .invoke(fec -> fec.setMqOffset(kafkaRecord.offset()))
          .flatMap(invocationExecutor::asyncExec);
      })
      .onFailure(InvocationException.class)
      .retry().atMost(3)
      .onFailure()
      .recoverWithItem(this::handleFailInvocation)
      .subscribe()
      .with(ctx -> {
        if (ctx!=null && ctx.getOutput()!=null)
          objCompPublisher.publish(ctx.getOutput().getId());
        next(kafkaRecord);
      }, error -> {
        LOGGER.error("Get unrecovery repeating error on invoker ", error);
        next(kafkaRecord);
      });
  }

  private boolean detectDuplication(KafkaConsumerRecord<String, Buffer> kafkaRecord,
                                    FunctionExecContext ctx) {
    var obj = ctx.isImmutable() ? ctx.getOutput():ctx.getMain();
    return obj.getStatus().getUpdatedOffset() >= kafkaRecord.offset();
  }

  FunctionExecContext handleFailInvocation(Throwable exception) {
    if (exception instanceof InvocationException invocationException) {
      var msg = invocationException.getCause()!=null ? invocationException
        .getCause().getMessage():null;
      if (LOGGER.isWarnEnabled())
        LOGGER.warn("Catch invocation fail on '{}' with message '{}'",
          invocationException.getTaskCompletion().getId().encode(),
          msg,
          invocationException
        );
      // TODO send to dead letter topic
    } else {
      LOGGER.error("Unexpected exception", exception);
    }
    return null;
  }

  void logLatency(KafkaConsumerRecord<?, ?> kafkaRecord) {
    var submittedTs = kafkaRecord.timestamp();
    LOGGER.debug("{}: record[{}]: Kafka latency {} ms",
      name,
      kafkaRecord.key(),
      System.currentTimeMillis() - submittedTs
    );
  }
}