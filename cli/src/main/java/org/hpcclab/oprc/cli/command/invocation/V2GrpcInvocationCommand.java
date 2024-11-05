package org.hpcclab.oprc.cli.command.invocation;

import com.github.f4b6a3.tsid.Tsid;
import com.google.protobuf.ByteString;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.vertx.core.Vertx;
import io.vertx.grpc.VertxChannelBuilder;
import jakarta.inject.Inject;
import org.hpcclab.oaas.model.invocation.InvocationStats;
import org.hpcclab.oaas.model.invocation.InvocationStatus;
import org.hpcclab.oaas.model.object.GOObject;
import org.hpcclab.oaas.model.object.JsonBytes;
import org.hpcclab.oaas.model.object.OMeta;
import org.hpcclab.oaas.model.proto.DSMap;
import org.hpcclab.oaas.model.state.OaasObjectState;
import org.hpcclab.oaas.proto.v2.InvocationRequest;
import org.hpcclab.oaas.proto.v2.InvocationResponse;
import org.hpcclab.oaas.proto.v2.MutinyOprcFunctionGrpc;
import org.hpcclab.oaas.proto.v2.ObjectInvocationRequest;
import org.hpcclab.oprc.cli.conf.ConfigFileManager;
import org.hpcclab.oprc.cli.mixin.CommonOutputMixin;
import org.hpcclab.oprc.cli.service.OutputFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
  name = "grpc-invoke2",
  aliases = {"ginv2", "gi2"},
  description = "Invoke a function with gRPC (API V2)",
  mixinStandardHelpOptions = true
)
@RegisterForReflection(
  targets = {
    InvocationRequest.class,
    InvocationResponse.class,
    InvocationStats.class,
    InvocationStatus.class,
    GOObject.class,
    OMeta.class,
    JsonBytes.class,
    OaasObjectState.class,
    DSMap.class,
    InvocationStats.class
  }
)
public class V2GrpcInvocationCommand implements Callable<Integer> {
  private static final Logger logger = LoggerFactory.getLogger(V2GrpcInvocationCommand.class);
  @CommandLine.Mixin
  CommonOutputMixin commonOutputMixin;
  @CommandLine.Option(names = "-c")
  String cls;
  @CommandLine.Option(names = "-p", defaultValue = "0")
  int partition;
  @CommandLine.Option(names = {"-m", "--main"})
  Optional<String> objectId;
  @CommandLine.Parameters(index = "0", defaultValue = "")
  String fb;
  @CommandLine.Option(names = "--args")
  Map<String, String> args;
  @CommandLine.Option(names = {"-b", "--pipe-body"}, defaultValue = "false")
  boolean pipeBody;
  @CommandLine.Option(names = {"-s", "--save"}, description = "save the object id to config file")
  boolean save;
  @Inject
  OutputFormatter outputFormatter;
  @Inject
  ConfigFileManager fileManager;
  @Inject
  Vertx vertx;

  MutinyOprcFunctionGrpc.MutinyOprcFunctionStub createGateway() throws IOException {
    String gatewayUrl = fileManager.current().getGatewayUrl();
    var url = URI.create(gatewayUrl).toURL();
    VertxChannelBuilder builder = VertxChannelBuilder.forAddress(vertx,
        url.getHost(),
        url.getPort() > 0? url.getPort() : url.getDefaultPort()
      )
      .disableRetry();
    if (url.getProtocol().equals("http"))
      builder.usePlaintext();
    var channel = builder
      .build();
    return MutinyOprcFunctionGrpc.newMutinyStub(channel);
  }

  @Override
  public Integer call() throws Exception {
    if (objectId.isEmpty()) {
      InvocationRequest.Builder builder = InvocationRequest.newBuilder()
        .setClsId(cls)
        .setFnId(fb);
      if (pipeBody) {
        var body = System.in.readAllBytes();
        builder.setPayload(ByteString.copyFrom(body));
      }
      var gateway = createGateway();
      var resp = gateway.invokeFn(builder.build()).await().indefinitely();
      System.out.print(resp.getPayload());
    } else {
      ObjectInvocationRequest.Builder builder = ObjectInvocationRequest.newBuilder()
        .setClsId(cls)
        .setPartitionId(partition)
        .setObjectId(Tsid.from(objectId.get()).toLong())
        .setFnId(fb);
      if (pipeBody) {
        var body = System.in.readAllBytes();
        builder.setPayload(ByteString.copyFrom(body));
      }
      var gateway = createGateway();
      var resp = gateway.invokeObj(builder.build()).await().indefinitely();
      System.out.print(resp.getPayload());
    }
    return 0;
  }
}
