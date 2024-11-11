package org.hpcclab.oaas.model.pkg;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author Pawissanutt
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OClassDeployment{
  @JsonProperty("_key")
  String key;
  int partitionCount;
  int replicaCount;
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  List<String> targetEnvs = List.of();
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  List<PartitionDeployment> partitions = List.of();

  @Data
  @Accessors(chain = true)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class PartitionDeployment {
    int partitionId;
    List<ReplicaDeployment> replicas = List.of();
  }

  @Data
  @Accessors(chain = true)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class ReplicaDeployment{
    int replicaId;
    String env;
    long crId;
  }
}
