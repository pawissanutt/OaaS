package org.hpcclab.oprc.cli.command.dev;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Inject;
import org.hpcclab.oaas.invocation.InvocationManager;
import org.hpcclab.oaas.invocation.InvocationReqHandler;
import org.hpcclab.oaas.model.invocation.InvocationRequest;
import org.hpcclab.oaas.model.invocation.InvocationResponse;
import org.hpcclab.oaas.model.object.JsonBytes;
import org.hpcclab.oaas.model.object.OOUpdate;
import org.hpcclab.oaas.model.task.OTask;
import org.hpcclab.oaas.model.task.OTaskCompletion;
import org.hpcclab.oprc.cli.conf.ConfigFileManager;
import org.hpcclab.oprc.cli.mixin.CommonOutputMixin;
import org.hpcclab.oprc.cli.service.DevServerService;
import org.hpcclab.oprc.cli.service.OutputFormatter;
import org.hpcclab.oprc.cli.state.LocalDevManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
  name = "invoke",
  aliases = {"inv", "i"},
  description = "Invoke a function with REST API",
  mixinStandardHelpOptions = true
)
@RegisterForReflection(
  targets = {
    OTask.class,
    OTaskCompletion.class,
    OOUpdate.class
  }
)
public class DevInvocationCommand implements Callable<Integer> {
  private static final Logger logger = LoggerFactory.getLogger(DevInvocationCommand.class);
  @CommandLine.Mixin
  CommonOutputMixin commonOutputMixin;
  @CommandLine.Option(names = "-c")
  String cls;
  @CommandLine.Option(names = {"-m", "--main"})
  String main;
  @CommandLine.Parameters(index = "0", defaultValue = "")
  String fb;
  @CommandLine.Option(names = "--args")
  Map<String, String> args = new HashMap<>();

  @CommandLine.Option(names = {"-b", "--pipe-body"}, defaultValue = "false")
  boolean pipeBody;
  @CommandLine.Option(names = {"-d", "--body"})
  String data;
  @CommandLine.Option(names = {"-s", "--save"}, description = "save the object id to config file")
  boolean save;
  @Inject
  OutputFormatter outputFormatter;
  @Inject
  ConfigFileManager fileManager;
  @Inject
  InvocationManager invocationManager;
  @Inject
  LocalDevManager devManager;
  @Inject
  DevServerService devServerService;

  @Override
  public Integer call() throws Exception {
    var conf = fileManager.dev();
    int port = fileManager.getOrCreate().getLocalDev().port();
    if (cls==null) cls = conf.getDefaultClass();
    if (main==null) main = conf.getDefaultObject();
    else if (main.isBlank() || main.equals("none") || main.equals("null")) main = null;

    JsonBytes body;
    if (pipeBody) {
      var inData = System.in.readAllBytes();
      body = new JsonBytes(inData);
    } else if (data != null){
      body = new JsonBytes(data.getBytes());
    } else {
      body = null;
    }
    InvocationReqHandler reqHandler = invocationManager.getReqHandler();
    InvocationRequest req = InvocationRequest.builder()
      .cls(cls)
      .fb(fb)
      .main(main)
      .args(args)
      .body(body)
      .build();
    logger.debug("use {}", req);
    devServerService.start(port);
    InvocationResponse resp = reqHandler.invoke(req).await().indefinitely();
    outputFormatter.printObject2(commonOutputMixin.getOutputFormat(), resp);
    var out = resp.output();
    devManager.persistObject();
    if (save && out != null && !out.getKey().isEmpty()) {
      conf.setDefaultObject(out.getKey());
      String outCls = out.getMeta().getCls();
      conf.setDefaultClass(outCls);
      fileManager.updateDev(conf);
    }
    return 0;
  }

}
