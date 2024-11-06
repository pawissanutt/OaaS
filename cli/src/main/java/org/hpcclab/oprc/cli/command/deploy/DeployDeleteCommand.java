package org.hpcclab.oprc.cli.command.deploy;

import io.vertx.mutiny.uritemplate.UriTemplate;
import io.vertx.mutiny.uritemplate.Variables;
import jakarta.inject.Inject;
import org.hpcclab.oprc.cli.mixin.CommonOutputMixin;
import org.hpcclab.oprc.cli.service.WebRequester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(
  name = "delete",
  aliases = {"d", "rm", "remove"},
  description = "Delete deployment",
  mixinStandardHelpOptions = true
)
public class DeployDeleteCommand implements Callable<Integer> {
  private static final Logger logger = LoggerFactory.getLogger(DeployDeleteCommand.class);
  @CommandLine.Mixin
  CommonOutputMixin commonOutputMixin;
  @Inject
  WebRequester webRequester;

  @CommandLine.Parameters(defaultValue = "")
  String key;

  @Override
  public Integer call() throws Exception {
    return webRequester.pmDeleteAndPrint(
      UriTemplate.of("/api/deployments/{+key}")
        .expandToString(Variables.variables()
          .set("key", key)
        ),
      commonOutputMixin.getOutputFormat()
    );
  }
}
