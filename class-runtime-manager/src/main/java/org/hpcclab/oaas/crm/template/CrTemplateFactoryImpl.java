package org.hpcclab.oaas.crm.template;

import jakarta.enterprise.context.ApplicationScoped;
import org.hpcclab.oaas.crm.CrmConfig;
import org.hpcclab.oaas.crm.CrtMappingConfig;
import org.hpcclab.oaas.crm.env.EnvironmentManager;
import org.hpcclab.oaas.crm.optimize.CpuBasedQoSOptimizer;
import org.hpcclab.oaas.crm.optimize.QosOptimizer;
import org.hpcclab.oaas.model.exception.StdOaasException;

/**
 * @author Pawissanutt
 */
@ApplicationScoped
public class CrTemplateFactoryImpl implements CrTemplateFactory {
  public static final String DEFAULT = "default";

  final EnvironmentManager environmentManager;
  final CrmConfig crmConfig;

  public CrTemplateFactoryImpl(EnvironmentManager environmentManager, CrmConfig crmConfig) {
    this.environmentManager = environmentManager;
    this.crmConfig = crmConfig;
  }


  @Override
  public CrTemplate create(String name, CrtMappingConfig.CrtConfig config) {
    if (config.type().equalsIgnoreCase("v1") ||
      config.type().equalsIgnoreCase("default")) {
      return new V1CrTemplate(
        name,
        environmentManager,
        this::selectOptimizer,
        config,
        crmConfig
      );
    }
    else if (config.type().equalsIgnoreCase("v2alpha")) {
      return new V2AlphaCrTemplate(
        name,
        environmentManager,
        this::selectOptimizer,
        config,
        crmConfig
      );
    } else {
      throw new StdOaasException("No available CR template with type " + config.type());
    }
  }

  public QosOptimizer selectOptimizer(CrtMappingConfig.CrtConfig config) {
    return new CpuBasedQoSOptimizer(config);
  }
}
