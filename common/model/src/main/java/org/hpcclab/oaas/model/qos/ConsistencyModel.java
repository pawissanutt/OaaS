package org.hpcclab.oaas.model.qos;

/**
 * @author Pawissanutt
 */
public enum ConsistencyModel {
  NONE,
  READ_YOUR_WRITE,
  BOUNDED_STALENESS,
  STRONG
}
