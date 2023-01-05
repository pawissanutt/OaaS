package org.hpcclab.oaas.storage.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.f4b6a3.tsid.Tsid;
import com.github.f4b6a3.tsid.TsidCreator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.hamcrest.Matchers;
import org.hpcclab.oaas.model.data.AccessLevel;
import org.hpcclab.oaas.model.data.DataAccessContext;
import org.hpcclab.oaas.model.object.ObjectType;
import org.hpcclab.oaas.model.cls.OaasClass;
import org.hpcclab.oaas.model.state.KeySpecification;
import org.hpcclab.oaas.model.state.StateSpecification;
import org.hpcclab.oaas.model.state.StateType;
import org.hpcclab.oaas.repository.ClassRepository;
import org.hpcclab.oaas.repository.TsidGenerator;
import org.hpcclab.oaas.storage.ArangoResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(ArangoResource.class)
class DataAccessResourceTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataAccessResourceTest.class);

  @Inject
  ClassRepository clsRepo;

  @BeforeEach
  public void setup() {
    var testCls = new OaasClass();
    testCls.setName("test");
    testCls.setObjectType(ObjectType.SIMPLE);
    testCls.setStateType(StateType.FILES);
    testCls.setStateSpec(new StateSpecification()
      .setKeySpecs(List.of(
        new KeySpecification("test", "s3")
      ))
    );
    clsRepo.put("test", testCls);
  }

  @Test
  void test() {
    var ctx = new DataAccessContext()
      .setId(TsidCreator.getTsid1024().toString())
      .setVid(TsidCreator.getTsid1024().toString())
      .setCls("test")
      .setLevel(AccessLevel.UNIDENTIFIED);
    var ctxKey = ctx.encode();
    given()
      .pathParam("oid", ctx.getId())
      .pathParam("key", "test")
      .queryParam("contextKey", ctxKey)
      .when().redirects().follow(false)
      .get("/contents/{oid}/{key}")
      .then()
      .log().ifValidationFails()
      .statusCode(Matchers.is(307));
  }
}
