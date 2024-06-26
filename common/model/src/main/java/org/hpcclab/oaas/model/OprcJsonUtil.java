package org.hpcclab.oaas.model;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ByteArraySerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * @author Pawissanutt
 */
public class OprcJsonUtil {

  public static SimpleModule createModule() {
    SimpleModule module = new SimpleModule("module");
    module.addSerializer(JsonObject.class, new JsonSerializer<JsonObject>() {
      @Override
      public void serialize(JsonObject value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeObject(value.getMap());
      }
    });
    module.addDeserializer(JsonObject.class, new JsonDeserializer<JsonObject>() {
      @Override
      public JsonObject deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return new JsonObject(p.readValueAs(Map.class));
      }
    });
    module.addSerializer(JsonArray.class, new JsonSerializer<JsonArray>() {
      @Override
      public void serialize(JsonArray value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeObject(value.getList());
      }
    });


    module.addDeserializer(JsonArray.class, new JsonDeserializer<JsonArray>() {
      @Override
      public JsonArray deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return new JsonArray(p.readValueAs(List.class));
      }
    });
    return module;
  }
}
