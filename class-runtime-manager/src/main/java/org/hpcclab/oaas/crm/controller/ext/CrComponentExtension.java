package org.hpcclab.oaas.crm.controller.ext;

import io.fabric8.kubernetes.api.model.HasMetadata;
import org.hpcclab.oaas.crm.controller.AbstractK8sCrComponentController;
import org.hpcclab.oaas.crm.optimize.CrAdjustmentPlan;
import org.hpcclab.oaas.crm.optimize.CrDeploymentPlan;

import java.util.List;

/**
 * @author Pawissanutt
 */
public interface CrComponentExtension {
  default void applyOnCreate(List<HasMetadata> item,
                                  CrDeploymentPlan plan,
                                          AbstractK8sCrComponentController controller) {
  }

  default void applyOnAdjust(List<HasMetadata> item, CrAdjustmentPlan plan,
                                          AbstractK8sCrComponentController controller) {
  }

  default void applyOnDelete(List<HasMetadata> item,
                                          AbstractK8sCrComponentController controller) {
  }

  default String name() {
    return this.getClass().getSimpleName();
  }
}
