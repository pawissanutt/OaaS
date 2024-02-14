package org.hpcclab.oaas.invoker.service;

import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;
import io.vertx.mutiny.kafka.client.consumer.KafkaConsumerRecord;
import org.hpcclab.oaas.invocation.ContextLoader;
import org.hpcclab.oaas.invocation.InvocationExecutor;
import org.hpcclab.oaas.invocation.OffLoader;
import org.hpcclab.oaas.invocation.applier.UnifiedFunctionRouter;
import org.hpcclab.oaas.invocation.dataflow.OneShotDataflowInvoker;
import org.hpcclab.oaas.invoker.ispn.repo.EIspnObjectRepository;
import org.hpcclab.oaas.model.exception.InvocationException;
import org.hpcclab.oaas.model.invocation.InvocationContext;
import org.hpcclab.oaas.model.invocation.InvocationRequest;
import org.hpcclab.oaas.repository.ObjectRepoManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Consumer;


public class RouterInvocationRecordHandler implements InvocationRecordHandler {

  private static final Logger logger = LoggerFactory.getLogger(RouterInvocationRecordHandler.class);
  final OffLoader invoker;
  final InvocationExecutor invocationExecutor;
  final ContextLoader loader;
  final UnifiedFunctionRouter router;
  final OneShotDataflowInvoker dataflowInvoker;
  final ObjectRepoManager objectRepoManager;

  public RouterInvocationRecordHandler(OffLoader invoker,
                                       InvocationExecutor invocationExecutor,
                                       ContextLoader loader,
                                       UnifiedFunctionRouter router,
                                       OneShotDataflowInvoker dataflowInvoker,
                                       ObjectRepoManager objectRepoManager) {
    this.invoker = invoker;
    this.invocationExecutor = invocationExecutor;
    this.loader = loader;
    this.router = router;
    this.dataflowInvoker = dataflowInvoker;
    this.objectRepoManager = objectRepoManager;
  }

  @Override
  public void handleRecord(KafkaConsumerRecord<String, Buffer> kafkaRecord, InvocationRequest request, Consumer<KafkaConsumerRecord<String, Buffer>> completionHandler, boolean skipDeduplication) {
    if (logger.isDebugEnabled()) {
      logDebug(kafkaRecord, request);
    }
    if (request.macro()) {
      handleMacro(kafkaRecord, request, completionHandler);
    } else {
      invokeTask(kafkaRecord, request, completionHandler, skipDeduplication);
    }

  }

  private void handleMacro(KafkaConsumerRecord<String, Buffer> kafkaRecord,
                           InvocationRequest request,
                           Consumer<KafkaConsumerRecord<String, Buffer>> completionHandler) {
    loader.loadCtxAsync(request)
      .flatMap(router::apply)
      .flatMap(ctx -> {
        var macro = ctx.getFunction().getMacro();
        if (macro!=null && macro.isAtomic()) {
          return dataflowInvoker.invoke(ctx);
        } else {
          return invocationExecutor.disaggregateMacro(ctx);
        }
      })
      .onFailure().retry()
      .withBackOff(Duration.ofMillis(100))
      .atMost(3)
      .subscribe()
      .with(
        ctx -> completionHandler.accept(kafkaRecord),
        error -> {
          logger.error("Unexpected error on invoker ", error);
          completionHandler.accept(kafkaRecord);
        }
      );
  }

  private void invokeTask(KafkaConsumerRecord<String, Buffer> kafkaRecord,
                          InvocationRequest request,
                          Consumer<KafkaConsumerRecord<String, Buffer>> completionHandler,
                          boolean skipDeduplication) {
    if (logger.isDebugEnabled())
      logger.debug("invokeTask [{},{}] {}", request.main(), kafkaRecord.offset(), request);
    loader.loadCtxAsync(request)
      .flatMap(ctx -> {
        if (!skipDeduplication && detectDuplication(kafkaRecord, ctx)) {
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
      .with(
        ctx -> completionHandler.accept(kafkaRecord),
        error -> {
          logger.error("Get an unrecoverable repeating error on invoker ", error);
          completionHandler.accept(kafkaRecord);
        });
  }

  private boolean detectDuplication(KafkaConsumerRecord<String, Buffer> kafkaRecord,
                                    InvocationContext ctx) {
    var obj = ctx.getMain();
    if (ctx.isImmutable())
      return false;
    if (obj.getLastOffset() < kafkaRecord.offset())
      return false;
    logger.warn("detect duplication [main={}, objOfs={}, reqOfs={}]",
      ctx.getRequest().main(),
      ctx.getMain().getLastOffset(),
      kafkaRecord.offset());
    return true;
  }

  InvocationContext handleFailInvocation(Throwable exception) {
    if (exception instanceof InvocationException invocationException) {
      if (logger.isWarnEnabled())
        logger.warn("Catch invocation fail on id='{}'",
          invocationException.getInvId(),
          invocationException
        );
      // TODO send to dead letter topic
    } else {
      logger.error("Unexpected exception", exception);
    }
    return null;
  }

  void logDebug(KafkaConsumerRecord<?, ?> kafkaRecord, InvocationRequest request) {
    var submittedTs = kafkaRecord.timestamp();
    var repo = objectRepoManager.getOrCreate(request.cls());
    var cache = ((EIspnObjectRepository) repo).getCache();
    var local = cache.getDistributionManager().getCacheTopology().getSegment(request.main());

    logger.debug("record[{},{},{}]: Kafka latency {} ms, locality[{}={}]",
      kafkaRecord.key(),
      request.invId(),
      request.macro(),
      System.currentTimeMillis() - submittedTs,
      kafkaRecord.partition(),
      local
    );
  }

}