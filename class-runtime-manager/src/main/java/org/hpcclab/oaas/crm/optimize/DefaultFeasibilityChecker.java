package org.hpcclab.oaas.crm.optimize;

import jakarta.inject.Singleton;
import org.hpcclab.oaas.crm.controller.CrController;
import org.hpcclab.oaas.crm.controller.CrOperation;
import org.hpcclab.oaas.crm.env.OprcEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DefaultFeasibilityChecker implements FeasibilityChecker {
  private static final Logger logger = LoggerFactory.getLogger( DefaultFeasibilityChecker.class );
  @Override
  public boolean deploymentCheck(OprcEnvironment.EnvConfig env, CrController cr, CrOperation operation) {
    return runtimeCheck(env, cr, operation);
  }

  @Override
  public boolean runtimeCheck(OprcEnvironment.EnvConfig env, CrController orbit, CrOperation operation) {
    return true;
//    if (env.feasibleCheckDisable())
//      return true;
//    var estimate = operation.estimate();
//    if (estimate.equals(OprcEnvironment.EnvResource.ZERO))
//      return true;
//    boolean feasible = env.usable().hasMore(estimate);
//    logger.info("orbit[{}] require {}, with feasibility [{}], with usable {}",
//      orbit.getId(), estimate, feasible, env.usable());
//    return feasible;
  }
}
