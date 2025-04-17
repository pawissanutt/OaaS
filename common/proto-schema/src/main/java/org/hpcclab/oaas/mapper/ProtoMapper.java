package org.hpcclab.oaas.mapper;

import com.google.protobuf.ByteString;
import io.vertx.core.json.JsonObject;
import org.hpcclab.oaas.model.cls.OClass;
import org.hpcclab.oaas.model.cr.CrHash;
import org.hpcclab.oaas.model.cr.OClassRuntime;
import org.hpcclab.oaas.model.cr.OcrRouting;
import org.hpcclab.oaas.model.function.OFunction;
import org.hpcclab.oaas.model.function.OFunctionConfig;
import org.hpcclab.oaas.model.function.OFunctionDeploymentStatus;
import org.hpcclab.oaas.model.pkg.OPackage;
import org.hpcclab.oaas.model.proto.DSMap;
import org.hpcclab.oaas.model.provision.ProvisionConfig;
import org.hpcclab.oaas.proto.*;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import java.util.Map;

@Mapper(collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
  nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
  nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface ProtoMapper {
  ProtoMapper INSTANCE = Mappers.getMapper( ProtoMapper.class );

  ProtoOClass toProto(OClass cls);

  ProtoOFunction toProto(OFunction fn);

  ProtoOFunctionConfig toProto(OFunctionConfig fn);


  ProtoOPackage toProto(OPackage pkg);

  ProtoOFunctionDeploymentStatus toProto(OFunctionDeploymentStatus status);

  OClass fromProto(ProtoOClass cls);


  OFunction fromProto(ProtoOFunction fn);

  OFunctionConfig fromProto(ProtoOFunctionConfig fn);

  OFunctionDeploymentStatus fromProto(ProtoOFunctionDeploymentStatus status);

  ProvisionConfig fromProto(ProtoProvisionConfig config);

  ProtoProvisionConfig toProto(ProvisionConfig config);

  OPackage fromProto(ProtoOPackage pkg);

  CrHash fromProto(ProtoCrHash crHashed);

  OClassRuntime fromProto(ProtoCr clsRuntime);
  OcrRouting.PartitionRouting fromProto(PartitionRouting routing);
  PartitionRouting toProto(OcrRouting.PartitionRouting routing);

  ProtoCrHash toProto(CrHash crHash);

  ProtoCr toProto(OClassRuntime clsRuntime);

  default DSMap map(Map<String, String> map) {
    if (map instanceof DSMap dsMap) return dsMap;
    return DSMap.copy(map);
  }

  default Map<String, String> map(DSMap map) {
    return map;
  }

  default byte[] convert(ByteString bytes) {
    if (bytes == null) return new byte[0];
    return bytes.toByteArray();
  }
  default ByteString convert(byte[] bytes) {
    if (bytes == null) return ByteString.EMPTY;
    return ByteString.copyFrom(bytes);
  }


  default Map<String, Object> toJsonMap(ByteString bytes) {
    if (bytes == null) return Map.of();
    if (bytes.isEmpty()) return Map.of();
    return new JsonObject(bytes.toStringUtf8()).getMap();
  }
  default ByteString fromJsonMap(Map<String, Object> map) {
    if (map == null) return ByteString.EMPTY;
    return ByteString.copyFromUtf8(new JsonObject(map).toString());
  }
}
