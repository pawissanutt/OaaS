package org.hpcclab.oaas.entity.function;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.Accessors;
import org.hpcclab.oaas.entity.EntityConverters;
import org.hpcclab.oaas.entity.OaasClass;
import org.hpcclab.oaas.model.function.OaasFunctionType;
import org.hpcclab.oaas.model.function.OaasFunctionValidation;
import org.hpcclab.oaas.model.function.OaasWorkflow;
import org.hpcclab.oaas.model.task.TaskConfiguration;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import static javax.persistence.CascadeType.*;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
public class OaasFunction {
  @NotBlank
  @Id
  String name;

  @NotNull
  @Enumerated
  OaasFunctionType type;

  @ManyToOne(fetch = FetchType.LAZY, cascade = {DETACH, REFRESH})
  @ToString.Exclude
  OaasClass outputCls;

  @Convert(converter = EntityConverters.ValidationConverter.class)
  @Column(columnDefinition = "jsonb")
  OaasFunctionValidation validation;

  @Convert(converter = EntityConverters.TaskConfigConverter.class)
  @Column(columnDefinition = "jsonb")
  TaskConfiguration task;

  @Convert(converter = EntityConverters.WorkflowConverter.class)
  @Column(columnDefinition = "jsonb")
  OaasWorkflow macro;
}