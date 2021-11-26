package org.hpcclab.oaas.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.hpcclab.oaas.entity.function.OaasFunctionBinding;
import org.hpcclab.oaas.model.object.OaasObjectType;
import org.hpcclab.oaas.model.state.OaasObjectState;
import org.hpcclab.oaas.model.state.StateSpecification;

import javax.persistence.*;
import java.util.Set;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NamedEntityGraph(
  name = "oaas.class.deep",
  attributeNodes = {
    @NamedAttributeNode(value = "functions", subgraph = "oaas.functionBinding.deep"),
  },
  subgraphs = {
    @NamedSubgraph(
      name = "oaas.functionBinding.deep",
      attributeNodes = @NamedAttributeNode(value = "function"
      ))
  })
@NamedEntityGraph(
  name = "oaas.class.find",
  attributeNodes = {
    @NamedAttributeNode(value = "functions")
  })
public class OaasClass {
  @Id
  String name;
  OaasObjectType objectType;
  OaasObjectState.StateType stateType;
  @ElementCollection()
  Set<OaasFunctionBinding> functions;
  @Convert(converter = EntityConverters.StateSpecificationConverter.class)
  @Column(columnDefinition = "jsonb")
  StateSpecification stateSpec;

  public void validate() {
    if (stateSpec==null) stateSpec = new StateSpecification();
  }
}
