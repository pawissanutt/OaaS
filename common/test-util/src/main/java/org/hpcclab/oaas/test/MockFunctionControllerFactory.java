package org.hpcclab.oaas.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hpcclab.oaas.invocation.InvocationReqHandler;
import org.hpcclab.oaas.invocation.LocationAwareInvocationForwarder;
import org.hpcclab.oaas.invocation.controller.fn.*;
import org.hpcclab.oaas.invocation.controller.fn.logical.NewFnController;
import org.hpcclab.oaas.invocation.controller.fn.logical.UpdateFnController;
import org.hpcclab.oaas.invocation.dataflow.DataflowOrchestrator;
import org.hpcclab.oaas.invocation.task.ContentUrlGenerator;
import org.hpcclab.oaas.invocation.task.DefaultContentUrlGenerator;
import org.hpcclab.oaas.invocation.task.OffLoaderFactory;
import org.hpcclab.oaas.model.data.DataAccessContext;
import org.hpcclab.oaas.model.function.OFunction;
import org.hpcclab.oaas.model.object.IOObject;
import org.hpcclab.oaas.repository.id.IdGenerator;
import org.hpcclab.oaas.repository.id.TsidGenerator;

public class MockFunctionControllerFactory implements FunctionControllerFactory {
  IdGenerator idGenerator = new TsidGenerator();
  ObjectMapper mapper = new ObjectMapper();
  OffLoaderFactory offLoaderFactory = new MockOffLoader.Factory();
  ContentUrlGenerator contentUrlGenerator;
  LocationAwareInvocationForwarder invocationForwarder;
  DataflowOrchestrator dataflowOrchestrator;

  public MockFunctionControllerFactory(InvocationReqHandler reqHandler) {
    contentUrlGenerator = new DefaultContentUrlGenerator("http://localhost:8090") {
      @Override
      public String generatePutUrl(IOObject<?> obj, DataAccessContext dac, String file) {
        // AVOID EXCEPTION
        return "";
      }
    };
    invocationForwarder = reqHandler::invoke;
    dataflowOrchestrator = new DataflowOrchestrator(invocationForwarder, idGenerator);
  }

  @Override
  public FunctionController create(OFunction function) {
    return switch (function.getType()) {
      case TASK -> new TaskFunctionController(idGenerator, mapper, offLoaderFactory, contentUrlGenerator);
      case BUILTIN -> createBuiltin(function);
      case MACRO -> new MacroFunctionController(
        idGenerator, mapper, dataflowOrchestrator
      );
      case CHAIN -> new ChainFunctionController(idGenerator, mapper);
      default -> throw new IllegalArgumentException("function %s not supported".formatted(function.getKey()));
    };
  }

  BuiltinFunctionController createBuiltin(OFunction function) {
    if (function.getKey().equals("builtin.new")) {
      return new NewFnController(idGenerator, mapper, contentUrlGenerator);
    } else if (function.getKey().equals("builtin.update")) {
      return new UpdateFnController(idGenerator, mapper);
    }
    throw new IllegalArgumentException();
  }
}
