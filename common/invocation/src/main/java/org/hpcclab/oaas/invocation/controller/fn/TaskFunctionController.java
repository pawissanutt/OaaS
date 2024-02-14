package org.hpcclab.oaas.invocation.controller.fn;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.hpcclab.oaas.invocation.OffLoader;
import org.hpcclab.oaas.invocation.controller.InvocationCtx;
import org.hpcclab.oaas.invocation.controller.SimpleStateOperation;
import org.hpcclab.oaas.invocation.task.ContentUrlGenerator;
import org.hpcclab.oaas.model.data.AccessLevel;
import org.hpcclab.oaas.model.data.DataAccessContext;
import org.hpcclab.oaas.model.object.OObject;
import org.hpcclab.oaas.model.state.StateType;
import org.hpcclab.oaas.model.task.OTask;
import org.hpcclab.oaas.model.task.TaskCompletion;
import org.hpcclab.oaas.repository.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Pawissanutt
 */
@Dependent
public class TaskFunctionController extends AbstractFunctionController {
  private static final Logger logger = LoggerFactory.getLogger(TaskFunctionController.class);
  final OffLoader offLoader;
  final ContentUrlGenerator contentUrlGenerator;

  @Inject
  public TaskFunctionController(IdGenerator idGenerator,
                                ObjectMapper mapper,
                                OffLoader offLoader,
                                ContentUrlGenerator contentUrlGenerator) {
    super(idGenerator, mapper);
    this.offLoader = offLoader;
    this.contentUrlGenerator = contentUrlGenerator;
  }

  @Override
  protected void validate(InvocationCtx ctx) {
  }

  @Override
  protected Uni<InvocationCtx> exec(InvocationCtx ctx) {
    ctx.setImmutable(functionBinding.isForceImmutable() || !function.getType().isMutable());
    if (functionBinding.getOutputCls()!=null) {
      var output = createOutput(ctx);
      ctx.setOutput(output);
    }
    OTask task = genTask(ctx);
    var uni = offLoader.offload(task);
    return uni
      .map(tc -> handleComplete(ctx, tc));
  }

  public OObject createOutput(InvocationCtx ctx) {
    var source = ctx.getMain();
    var obj = OObject.createFromClasses(outputCls);
    obj.setData(source.getData());
    obj.setRevision(0);
    var req = ctx.getRequest();
    var outId = req!=null ? req.outId():null;
    if (outId!=null && !outId.isEmpty()) {
      obj.setId(req.outId());
    } else {
      obj.setId(idGenerator.generate());
    }
    return obj;
  }

  public OTask genTask(InvocationCtx ctx) {
    var verId = ctx.getRequest().invId();
    var task = new OTask();
    task.setId(verId);
    task.setPartKey(ctx.getMain().getId());
    task.setFbName(functionBinding.getName());
    task.setMain(ctx.getMain());
    task.setFuncKey(function.getKey());
    task.setFunction(function);
    task.setInputs(ctx.getInputs());
    task.setImmutable(ctx.isImmutable());
    task.setArgs(resolveArgs(ctx));
    logger.info("function status: {}", function.getStatus());

    if (ctx.getOutput()!=null) {
      task.setOutput(ctx.getOutput());
      if (outputCls.getStateType()==StateType.COLLECTION ||
        !outputCls.getStateSpec().getKeySpecs().isEmpty()) {
        var dac = DataAccessContext.generate(task.getOutput(), AccessLevel.ALL, verId);
        task.setAllocOutputUrl(contentUrlGenerator.generateAllocateUrl(ctx.getOutput(), dac));
      }
    }

    if (function.getType().isMutable()) {
      var dac = DataAccessContext.generate(task.getMain(), AccessLevel.ALL, verId);
      task.setAllocMainUrl(contentUrlGenerator.generateAllocateUrl(ctx.getMain(), dac));
    }
    task.setMainKeys(genUrls(ctx.getMain(), ctx.getMainRefs(), AccessLevel.ALL));

    var inputContextKeys = new ArrayList<String>();
    if (ctx.getInputs()==null) ctx.setInputs(List.of());
    var inputs = ctx.getInputs();
    for (OObject inputObj : inputs) {
      AccessLevel level = cls.isSamePackage(inputObj.getCls()) ?
        AccessLevel.INTERNAL:AccessLevel.INVOKE_DEP;
      var b64Dac = DataAccessContext.generate(inputObj, level).encode();
      inputContextKeys.add(b64Dac);
    }
    task.setInputContextKeys(inputContextKeys);

    task.setTs(System.currentTimeMillis());
    return task;
  }


  public Map<String, String> genUrls(OObject obj,
                                     Map<String, OObject> refs,
                                     AccessLevel level) {
    Map<String, String> m = new HashMap<>();
    generateUrls(m, obj, refs, "", level);
    return m;
  }

  private void generateUrls(Map<String, String> map,
                            OObject obj,
                            Map<String, OObject> refs,
                            String prefix,
                            AccessLevel level) {

    var verIds = obj.getState().getVerIds();
    if (verIds!=null && !verIds.isEmpty()) {
      for (var vidEntry : verIds.entrySet()) {
        var dac = DataAccessContext.generate(obj, level,
          vidEntry.getValue());
        var url =
          contentUrlGenerator.generateUrl(obj, dac, vidEntry.getKey());
        map.put(prefix + vidEntry.getKey(), url);
      }
    }

    if (obj.getState().getOverrideUrls()!=null) {
      obj.getState().getOverrideUrls()
        .forEachKeyValue((k, v) -> map.put(prefix + k, v));
    }
    if (refs!=null) {
      for (var entry : refs.entrySet()) {
        generateUrls(
          map,
          entry.getValue(),
          null,
          prefix + entry.getKey() + ".",
          AccessLevel.UNIDENTIFIED);
      }
    }
  }

  public Map<String, String> resolveArgs(InvocationCtx ctx) {
    var defaultArgs = functionBinding.getDefaultArgs();
    if (ctx.getArgs()!=null && defaultArgs!=null) {
      var finalArgs = Maps.mutable.ofMap(defaultArgs);
      finalArgs.putAll(ctx.getArgs());
      return finalArgs;
    } else if (ctx.getArgs()==null && defaultArgs!=null) {
      return defaultArgs;
    } else if (ctx.getArgs()!=null) {
      return ctx.getArgs();
    }
    return Map.of();
  }

  public InvocationCtx handleComplete(InvocationCtx context, TaskCompletion completion) {
    validateCompletion(context, completion);
    updateState(context, completion);
    List<OObject> updateList = completion.getMain()!=null ?
      Lists.mutable.of(context.getMain()):
      List.of();
    List<OObject> createList = completion.getOutput()!=null ?
      List.of(context.getOutput()):
      List.of();
    context.setStateOperations(List.of(
      new SimpleStateOperation(createList, cls, updateList, outputCls)
    ));
    return context;
  }

  void updateState(InvocationCtx context, TaskCompletion completion) {
    var main = context.getMain();
    var out = context.getOutput();
    var log = context.initLog();
    log.updateStatus(completion);

    if (main!=null) {
      if (completion.getMain()!=null) {
        completion.getMain().update(main, log.getKey());
      }
      main.setLastOffset(context.getMqOffset());
      if (completion.isSuccess()) {
        main.setLastInv(completion.getId());
      }
    }

    if (out!=null) {
      if (completion.getOutput()!=null)
        completion.getOutput().update(out, completion
          .getId());
      if (completion.isSuccess()) {
        out.setLastInv(completion.getId());
      }
    }

    context.setRespBody(completion.getBody());
  }

  private void validateCompletion(InvocationCtx context, TaskCompletion completion) {
    if (completion.getMain()!=null) {
      var update = completion.getMain();
      if (!functionBinding.isForceImmutable()) {
        update.filterKeys(cls);
      }
    }
    if (context.getOutput()==null) {
      completion.setOutput(null);
    } else if (completion.getOutput()!=null) {
      var update = completion.getOutput();
      update.filterKeys(cls);
    }
  }
}