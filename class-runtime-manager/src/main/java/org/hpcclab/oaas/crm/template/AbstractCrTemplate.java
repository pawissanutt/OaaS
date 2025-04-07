package org.hpcclab.oaas.crm.template;

import com.github.f4b6a3.tsid.TsidFactory;
import org.hpcclab.oaas.crm.CrmConfig;
import org.hpcclab.oaas.crm.CrtMappingConfig;
import org.hpcclab.oaas.crm.env.EnvironmentManager;
import org.hpcclab.oaas.crm.optimize.QosOptimizer;

import java.util.Objects;
import java.util.function.Function;

public abstract class AbstractCrTemplate implements CrTemplate {
  protected final TsidFactory tsidFactory;
  protected final CrtMappingConfig.CrtConfig config;
  protected final QosOptimizer qosOptimizer;
  protected final String name;
  protected final CrmConfig crmConfig;
  protected final EnvironmentManager environmentManager;

  protected AbstractCrTemplate(String name,
                               EnvironmentManager environmentManager,
                               CrtMappingConfig.CrtConfig config,
                               Function<CrtMappingConfig.CrtConfig, QosOptimizer> optimizerBuilder,
                               CrmConfig crmConfig) {

    this.name = name;
    this.crmConfig = crmConfig;
    this.environmentManager = environmentManager;
    Objects.requireNonNull(config);
    this.config = validate(config);
    Objects.requireNonNull(optimizerBuilder);
    this.qosOptimizer = optimizerBuilder.apply(this.config);
    this.tsidFactory = TsidFactory.newInstance1024();

  }

  @Override
  public QosOptimizer getQosOptimizer() {
    return qosOptimizer;
  }

  protected CrtMappingConfig.CrtConfig validate(CrtMappingConfig.CrtConfig crtConfig) {
    var func = crtConfig.functions();
    if (func==null) {
      func = CrtMappingConfig.FnConfig.builder()
        .stabilizationWindow(20000)
        .defaultMaxScale(10)
        .build();
    }
    if (func.defaultMaxScale() <= 0) {
      func = func.toBuilder().defaultMaxScale(10).build();
    }
    if (func.maxScaleStep() <= 0) {
      func = func.toBuilder().maxScaleStep(3).build();
    }
    String optimizer = crtConfig.optimizer();
    if (optimizer==null) optimizer = "default";
    return crtConfig.toBuilder()
      .functions(func)
      .optimizer(optimizer)
      .build();
  }

  @Override
  public String name() {
    return name;
  }
}
