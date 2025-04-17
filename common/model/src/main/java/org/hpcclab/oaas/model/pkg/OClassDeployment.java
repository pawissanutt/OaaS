package org.hpcclab.oaas.model.pkg;

import com.fasterxml.jackson.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * @author Pawissanutt
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OClassDeployment {
  @JsonAlias("key")
  @JsonProperty("_key")
  String key;
  int partitionCount;
  int replicaCount;
  @JsonSetter(nulls = Nulls.AS_EMPTY)
  List<String> targetEnvs = List.of();
  List<MemberGroup> members = List.of();
  String shardType = "basic";
  List<ShardAssignment> assignments = List.of();
  Map<String, String> options = Map.of();

  @Data
  @Accessors(chain = true)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class MemberGroup {
    long id;
    String env;
    int maxShards=-1;
    boolean allPartitions = false;
    List<String> disabledFn = List.of();
    List<String> standbyFn = List.of();
    String forceTemplate = "";
  }

  @Data
  @Accessors(chain = true)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class ShardAssignment {
    long primary = -1L;
    List<Long> owners;
    List<Long> shardIds;
  }

//  @Data
//  @Accessors(chain = true)
//  @JsonInclude(JsonInclude.Include.NON_EMPTY)
//  public static class Partition {
//    int partitionId;
//    List<ReplicaDeployment> replicas = List.of();
//  }
//
//  @Data
//  @Accessors(chain = true)
//  @JsonInclude(JsonInclude.Include.NON_EMPTY)
//  public static class ReplicaDeployment {
//    int replicaId;
//    String env;
//    long crId;
//  }


}
