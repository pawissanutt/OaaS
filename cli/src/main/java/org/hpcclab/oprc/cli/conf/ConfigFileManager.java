package org.hpcclab.oprc.cli.conf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.collections.api.factory.Maps;
import org.hpcclab.oprc.cli.CliConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * @author Pawissanutt
 */
@ApplicationScoped
public class ConfigFileManager {
  final CliConfig cliConfig;
  ObjectMapper objectMapper;
  FileCliConfig fileCliConfig;

  public ConfigFileManager(CliConfig cliConfig,
                           ObjectMapper mapper) {
    this.cliConfig = cliConfig;
    objectMapper = mapper.copyWith(new YAMLFactory()
      .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
      .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
    );
  }

  public FileCliConfig.FileCliContext current() throws IOException {
    return getOrCreate().current();
  }

  public FileCliConfig.FileCliContext dev() throws IOException {
    FileCliConfig.FileCliContext dev = getOrCreate().contexts.get("dev");
    if (dev == null) {
      dev = FileCliConfig.FileCliContext.builder().build();
      getOrCreate().contexts.put("dev", dev);
    }
    return dev;
  }

  public FileCliConfig createDefault() {
    var defaultCtx = FileCliConfig.FileCliContext.builder()
      .pmUrl("http://pm.oaas.127.0.0.1.nip.io")
      .gatewayUrl("http://oaas.127.0.0.1.nip.io")
      .invUrl("http://inv.oaas.127.0.0.1.nip.io")
      .defaultClass("example.record")
      .build();
    return new FileCliConfig(
      Maps.mutable.of("default", defaultCtx),
      "default"
    );
  }

  public FileCliConfig getOrCreate() throws IOException {
    if (fileCliConfig != null) return fileCliConfig;
    String homeDir = System.getProperty("user.home");
    Path configFilePath = Path.of(homeDir).resolve(cliConfig.configPath());
    var file = configFilePath.toFile();
    if (file.exists()) {
      fileCliConfig = objectMapper.readValue(file, FileCliConfig.class);
    } else {
      file.getParentFile().mkdirs();
      fileCliConfig = createDefault();
      objectMapper.writeValue(file, fileCliConfig);
    }
    return fileCliConfig;
  }

  public void update(FileCliConfig config) throws IOException {
    String homeDir = System.getProperty("user.home");
    Path configFilePath = Path.of(homeDir).resolve(cliConfig.configPath());
    var file = configFilePath.toFile();
    writeConf(file, config);
  }

  public void update(FileCliConfig.FileCliContext ctx) throws IOException {
    String homeDir = System.getProperty("user.home");
    Path configFilePath = Path.of(homeDir).resolve(cliConfig.configPath());
    var file = configFilePath.toFile();
    var conf = getOrCreate();
    conf.getContexts().put(conf.getCurrentContext(), ctx);
    writeConf(file, conf);
  }

  public void updateDev(FileCliConfig.FileCliContext ctx) throws IOException {
    String homeDir = System.getProperty("user.home");
    Path configFilePath = Path.of(homeDir).resolve(cliConfig.configPath());
    var file = configFilePath.toFile();
    var conf = getOrCreate();
    conf.getContexts().put("dev", ctx);
    writeConf(file, conf);
  }

  private void writeConf(File file, FileCliConfig conf) throws IOException {
    if (file.exists()) {
      objectMapper.writeValue(file, conf);
    } else {
      file.getParentFile().mkdirs();
      objectMapper.writeValue(file, conf);
    }
  }
}
