package org.hpcclab.oaas.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.smallrye.mutiny.Uni;
import org.hpcclab.oaas.mapper.OaasMapper;
import org.hpcclab.oaas.model.cls.DeepOaasClass;
import org.hpcclab.oaas.model.proto.OaasClass;
import org.hpcclab.oaas.repository.IfnpOaasClassRepository;
import org.hpcclab.oaas.iface.service.ClassService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.util.List;

@ApplicationScoped
public class ClassResource implements ClassService {
  private static final Logger LOGGER = LoggerFactory.getLogger( ClassResource.class );
  @Inject
  IfnpOaasClassRepository classRepo;
  @Inject
  OaasMapper oaasMapper;
  ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());


  public Uni<List<OaasClass>> list(Integer page, Integer size) {
    if (page == null) page = 0;
    if (size == null) size = 100;
    var list = classRepo.pagination(page, size);
    return Uni.createFrom().item(list);
  }

  @Override
  public Uni<OaasClass> create(boolean update, OaasClass cls) {
    cls.validate();
    return classRepo.persist(cls);
  }

  @Override
  public Uni<OaasClass> patch(String name, OaasClass clsPatch) {
    return classRepo.getAsync(name)
      .onItem().ifNull().failWith(NotFoundException::new)
      .flatMap(cls -> {
        oaasMapper.set(clsPatch, cls);
        cls.validate();
        return classRepo.persist(cls);
      });
  }

  @Override
  public Uni<OaasClass> createByYaml(boolean update, String body) {
    try {
      var cls = yamlMapper.readValue(body, OaasClass.class);
      return create(update, cls);
    } catch (JsonProcessingException e) {
      throw new BadRequestException(e);
    }
  }

  @Override
  public Uni<OaasClass> get(String name) {
    return classRepo.getAsync(name)
      .onItem().ifNull().failWith(NotFoundException::new);
  }

  @Override
  public Uni<DeepOaasClass> getDeep(String name) {
    return classRepo.getDeep(name);
  }

  @Override
  public Uni<OaasClass> delete(String name) {
    return classRepo.removeAsync(name)
      .onItem().ifNull().failWith(NotFoundException::new);
  }
}
