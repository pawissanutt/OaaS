package org.hpcclab.oaas.arango.repo;

import com.arangodb.ArangoCollection;
import com.arangodb.async.ArangoCollectionAsync;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.hpcclab.oaas.arango.CacheFactory;
import org.hpcclab.oaas.model.cls.OaasClass;
import org.hpcclab.oaas.model.exception.OaasValidationException;
import org.hpcclab.oaas.repository.ClassRepository;
import org.hpcclab.oaas.repository.ClassResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ArgClsRepository extends AbstractCachedArgRepository<OaasClass> implements ClassRepository {
  private static final Logger LOGGER = LoggerFactory.getLogger( ArgClsRepository.class );

  @Inject
  @Named("ClassCollection")
  ArangoCollection collection;
  @Inject
  @Named("ClassCollectionAsync")
  ArangoCollectionAsync collectionAsync;

  @Inject
  ClassResolver classResolver;

  @Inject
  CacheFactory cacheFactory;
  private Cache<String, OaasClass> cache;

  @PostConstruct
  void setup() {
    cache = cacheFactory.get();
  }

  @Override
  public ArangoCollection getCollection() {
    return collection;
  }

  @Override
  public ArangoCollectionAsync getAsyncCollection() {
    return collectionAsync;
  }

  @Override
  public Class<OaasClass> getValueCls() {
    return OaasClass.class;
  }

  @Override
  public String extractKey(OaasClass cls) {
    return cls.getKey();
  }

  @Override
  Cache<String, OaasClass> cache() {
    return cache;
  }

  public List<OaasClass> loadChildren(OaasClass cls) {
    var query = """
      FOR cls IN @@col
        FILTER cls.resolved.identities ANY == @key
        return cls
      """;
    return query(query,
      Map.of(
        "@col", getCollection().name(),
        "key", cls.getKey())
    );
  }

  public OaasClass resolveInheritance(OaasClass baseCls,
                                      Map<String, OaasClass> clsMap,
                                      Set<String> path) {
    if (path.contains(baseCls.getKey())){
      throw OaasValidationException.errorClassCyclicInheritance(path);
    }
    path.add(baseCls.getKey());
    if (baseCls.getParents() ==null) baseCls.setParents(List.of());
    LOGGER.info("resolve {} {}", baseCls, baseCls.getParents());
    var parentClasses = baseCls.getParents()
      .stream()
      .map(clsKey -> {
        OaasClass cls;
        if (clsMap.containsKey(clsKey))
          cls = clsMap.get(clsKey);
        else
          cls = get(clsKey);
        if (!cls.getResolved().isFlag()) {
          cls = resolveInheritance(cls, clsMap, path);
        }
        return cls;
      })
      .toList();
    var newCls = classResolver.resolve(baseCls, parentClasses);
    clsMap.put(baseCls.getKey(), newCls);
    return newCls;
  }

  public boolean checkCycle(OaasClass baseCls, OaasClass parent) {
    if (parent.getResolved().getIdentities()==null
      || parent.getResolved().getIdentities().isEmpty())
      return false;
    return (parent.getResolved().getIdentities().contains(baseCls.getKey()));
  }

  @Override
  public Map<String, OaasClass> resolveInheritance(Map<String, OaasClass> clsMap) {
    var startingClasses = List.copyOf(clsMap.values());
    var ctxMap = Maps.mutable.ofMap(clsMap);
    for (var cls : startingClasses) {
      cls.getResolved().setFlag(false);
      resolveInheritance(cls, ctxMap, Sets.mutable.empty());
    }
    for (var cls : startingClasses) {
      var children = loadChildren(cls);
      for (var child: children) {
        child.getResolved().setFlag(false);
        resolveInheritance(child, ctxMap, Sets.mutable.empty());
      }
    }
    return ctxMap;
  }
}
