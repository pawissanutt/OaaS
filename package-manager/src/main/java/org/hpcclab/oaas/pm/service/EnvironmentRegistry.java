package org.hpcclab.oaas.pm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.runtime.Startup;
import io.vertx.core.Vertx;
import io.vertx.grpc.VertxChannelBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hpcclab.oaas.model.exception.StdOaasException;
import org.hpcclab.oaas.pm.PkgManagerConfig;
import org.hpcclab.oaas.proto.CrManagerGrpc;
import org.hpcclab.oaas.proto.CrManagerGrpc.CrManagerBlockingStub;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
@Startup
public class EnvironmentRegistry {

  final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
  Config envConfig;
  Map<String, Environment> environmentMap;
  Map<String, CrManagerBlockingStub> crmClients = new ConcurrentHashMap<>();

  @Inject
  Vertx vertx;

  @Inject
  public EnvironmentRegistry(PkgManagerConfig config) {
    try {
      String envStr = config.env();
      envConfig = yamlMapper.readValue(envStr, Config.class);
      if (envConfig.defaultEnv==null || envConfig.defaultEnv.isBlank()) {
        throw new StdOaasException("defaultEnv must not be null or empty");
      }
      environmentMap = envConfig.environments
        .stream()
        .collect(
          Collectors.toMap(Environment::name, Function.identity()));
    } catch (JsonProcessingException e) {
      throw new StdOaasException(e);
    }

  }

  public Config getEnvConfig() {
    return envConfig;
  }

  public CrManagerBlockingStub getCrmStub(String env) {
    return crmClients.computeIfAbsent(env, k -> {
      var envConf = environmentMap.get(k);
      if (envConf==null) return null;
      return createStub(envConf.crmUrl);
    });
  }

  public CrManagerBlockingStub getCrmStub() {
    return getCrmStub(envConfig.defaultEnv);
  }

  CrManagerBlockingStub createStub(String crmUrl) {
    URL url = null;
    try {
      url = URI.create(crmUrl).toURL();
    } catch (MalformedURLException e) {
      throw new StdOaasException(e);
    }
    VertxChannelBuilder builder = VertxChannelBuilder.forAddress(vertx, url.getHost(),
        url.getPort())
      .disableRetry();
    if (url.getProtocol().equals("http"))
      builder.usePlaintext();
    var channel = builder
      .build();
    return CrManagerGrpc.newBlockingStub(channel);
  }


  public record Config(List<Environment> environments,
                       String defaultEnv) {

  }

  public record Environment(String name,
                            int id,
                            String crmUrl,
                            Map<String, String> options) {

  }
}
