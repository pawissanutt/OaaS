package org.hpcclab.msc;

import io.vertx.core.json.Json;
import org.hamcrest.Matchers;
import org.hpcclab.msc.object.entity.object.OaasObject;
import org.hpcclab.msc.object.model.DeepOaasObjectDto;
import org.hpcclab.msc.object.model.FunctionCallRequest;
import org.hpcclab.msc.object.model.OaasObjectDto;

import javax.ws.rs.core.MediaType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;


public class TestUtils {

  public static List<OaasObjectDto> listObject() {
    return Arrays.asList(given()
      .when().get("/api/objects")
      .then()
      .contentType(MediaType.APPLICATION_JSON)
      .statusCode(200)
      .extract().body().as(OaasObjectDto[].class));
  }

  public static OaasObjectDto create(OaasObjectDto o) {
    return given()
      .contentType(MediaType.APPLICATION_JSON)
      .body(Json.encodePrettily(o))
      .when().post("/api/objects")
      .then()
      .contentType(MediaType.APPLICATION_JSON)
      .statusCode(200)
      .body("id", Matchers.notNullValue())
      .extract().body().as(OaasObjectDto.class);
  }

  public static OaasObjectDto getObject(UUID id) {
    return given()
      .pathParam("id", id.toString())
      .when().get("/api/objects/{id}")
      .then()
      .contentType(MediaType.APPLICATION_JSON)
      .statusCode(200)
      .body("id", Matchers.equalTo(id.toString()))
      .extract().body().as(OaasObjectDto.class);
  }


  public static DeepOaasObjectDto getObjectDeep(UUID id) {
    return given()
      .pathParam("id", id.toString())
      .when().get("/api/objects/{id}/deep")
      .then()
      .contentType(MediaType.APPLICATION_JSON)
      .statusCode(200)
      .body("id", Matchers.equalTo(id.toString()))
      .extract().body().as(DeepOaasObjectDto.class);
  }

  public static OaasObjectDto bind(OaasObjectDto obj, List<String> functionNames) {
//    var meta = functionNames.stream()
//      .map(MscFuncMetadata::new)
//      .collect(Collectors.toList());
    return given()
      .contentType(MediaType.APPLICATION_JSON)
      .body(Json.encodePrettily(functionNames))
      .pathParam("oid", obj.getId().toString())
      .when().post("/api/objects/{oid}/binds")
      .then()
      .contentType(MediaType.APPLICATION_JSON)
      .statusCode(200)
      .body("id", Matchers.notNullValue())
      .extract().body().as(OaasObjectDto.class);
  }

  public static OaasObjectDto fnCall(FunctionCallRequest request) {
    return given()
      .contentType(MediaType.APPLICATION_JSON)
      .body(Json.encodePrettily(request))
      .pathParam("oid", request.getTarget().toString())
      .when().post("/api/objects/{oid}/r-exec")
      .then()
      .contentType(MediaType.APPLICATION_JSON)
      .statusCode(200)
      .body("id", Matchers.notNullValue())
      .extract().body().as(OaasObjectDto.class);
  }
}
