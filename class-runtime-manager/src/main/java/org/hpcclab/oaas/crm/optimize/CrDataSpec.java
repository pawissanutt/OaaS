package org.hpcclab.oaas.crm.optimize;

import lombok.Builder;
import org.hpcclab.oaas.proto.DataDistribution;

/**
 * @author Pawissanutt
 */
@Builder(toBuilder = true)
public record CrDataSpec(DataDistribution dist) {

//  @JsonProperty
//  String getDistRaw() throws InvalidProtocolBufferException {
//    return JsonFormat.printer().print(dist);
//  }
//  @JsonCreator
//  static CrDataSpec fromRaw(String distRaw) throws InvalidProtocolBufferException {
//    DataDistribution.Builder builder = DataDistribution.newBuilder();
//    JsonFormat.parser().merge(distRaw, builder);
//    return new CrDataSpec(builder.build());
//  }


  public static final CrDataSpec DEFAULT = new CrDataSpec(
    DataDistribution.newBuilder().build());
}
