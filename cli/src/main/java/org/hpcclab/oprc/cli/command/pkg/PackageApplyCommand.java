package org.hpcclab.oprc.cli.command.pkg;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.vertx.mutiny.uritemplate.UriTemplate;
import io.vertx.mutiny.uritemplate.Variables;
import jakarta.inject.Inject;
import org.hpcclab.oprc.cli.conf.ConfigFileManager;
import org.hpcclab.oprc.cli.mixin.CommonOutputMixin;
import org.hpcclab.oprc.cli.service.OutputFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(
  name = "apply",
  aliases = {"a", "create", "c"},
  description = "Apply a package",
  mixinStandardHelpOptions = true
)
public class PackageApplyCommand implements Callable<Integer> {
  private static final Logger logger = LoggerFactory.getLogger(PackageApplyCommand.class);
  @CommandLine.Parameters()
  File pkgFile;

  @CommandLine.Mixin
  CommonOutputMixin commonOutputMixin;
  @CommandLine.Option(names = {"--override-package", "-p"})
  String overridePackageName;

  @Inject
  ConfigFileManager fileManager;
  @Inject
  OutputFormatter outputFormatter;

  @Inject
  WebClient webClient;

  @Override
  public Integer call() throws Exception {
    var yamlMapper = YAMLMapper.builder().build();
    var pkg = Files.readString(pkgFile.toPath());
    var json = new JsonObject(yamlMapper.readValue(pkg, Map.class));
    if (overridePackageName!=null && !overridePackageName.isEmpty())
      json.put("name",overridePackageName);
    var res = webClient.postAbs(UriTemplate.of("{+oc}/api/packages")
        .expandToString(Variables.variables()
          .set("oc", fileManager.current().getPmUrl())))
      .sendJsonObject(json)
      .await().indefinitely();
    if (res.statusCode()!=200) {
      if (logger.isErrorEnabled())
        logger.error("Can not apply package: code={} body={}", res.statusCode(), res.bodyAsString());
      return res.statusCode();
    }

    outputFormatter.print(commonOutputMixin.getOutputFormat(), res.bodyAsJsonObject());
    return 0;
  }
}
