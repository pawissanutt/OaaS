package org.hpcclab.oaas.model.cr;

import java.util.List;
import java.util.Map;

/**
 * @author Pawissanutt
 */
public record OcrRouting( List<PartitionRouting> partitions) {

  public record PartitionRouting(Map<String, FunctionRouting> functions) {

  }

  public record FunctionRouting(String url){}
}
