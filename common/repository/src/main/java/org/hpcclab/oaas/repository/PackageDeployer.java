package org.hpcclab.oaas.repository;

import org.hpcclab.oaas.model.pkg.OPackage;

/**
 * @author Pawissanutt
 */
public interface PackageDeployer {
  void deploy(OPackage pkg);
  void detach(String clsKey);
}
