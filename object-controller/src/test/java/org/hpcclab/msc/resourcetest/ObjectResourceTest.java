package org.hpcclab.msc.resourcetest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import org.hamcrest.Matchers;
import org.hpcclab.msc.TestUtils;
import org.hpcclab.msc.object.entity.object.FileState;
import org.hpcclab.msc.object.entity.object.MscObject;
import org.hpcclab.msc.object.model.RootMscObjectCreating;
import org.hpcclab.msc.object.resource.ObjectResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;

import static io.restassured.RestAssured.given;

@QuarkusTest
public class ObjectResourceTest {


  @BeforeAll
  static void setup() {
    RestAssured.filters(new RequestLoggingFilter(),
      new ResponseLoggingFilter());
  }

  @Test
  void test() {
    var root = new MscObject()
      .setType(MscObject.Type.RESOURCE)
      .setState(new FileState().setFileUrl("http://test/test.m3u8"));
    root = TestUtils.create(root);
    Assertions.assertTrue(TestUtils.listObject().size() >=1);
    TestUtils.getObject(root.getId());
  }
}
