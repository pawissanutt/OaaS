package org.hpcclab.oaas.crm.optimize;

import lombok.Builder;
import org.hpcclab.oaas.proto.DataDistribution;

/**
 * @author Pawissanutt
 */
@Builder(toBuilder = true)
public record CrDataSpec(DataDistribution dist) {
  public static final CrDataSpec DEFAULT = new CrDataSpec(
    DataDistribution.newBuilder().build());
}
