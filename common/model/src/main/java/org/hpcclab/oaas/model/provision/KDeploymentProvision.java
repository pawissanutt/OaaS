package org.hpcclab.oaas.model.provision;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder(toBuilder = true)
public record KDeploymentProvision(
  String image,
  int minScale,
  int maxScale,
  String requestsCpu,
  String requestsMemory,
  String limitsCpu,
  String limitsMemory,
  String apiPath,
  int port,
  String pullPolicy
) {
}
