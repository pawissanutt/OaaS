package org.hpcclab.oaas.repository.impl;

import io.smallrye.mutiny.Uni;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.map.MutableMap;
import org.hpcclab.oaas.repository.EntityRepository;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MapEntityRepository<K,V> implements EntityRepository<K,V> {

  MutableMap<K,V> map;
  Function<V,K> keyExtractor;

  public MapEntityRepository(MutableMap<K, V> map,
                             Function<V,K> keyExtractor) {
    this.map = map;
    this.keyExtractor = keyExtractor;
  }

  @Override
  public V get(K key) {
    return map.get(key);
  }

  @Override
  public Uni<V> getAsync(K key) {
    return Uni.createFrom().item(get(key));
  }

  @Override
  public Map<K, V> list(Set<K> keys) {
    return map.select((k,v) -> keys.contains(k));
  }

  @Override
  public Uni<Map<K, V>> listAsync(Set<K> keys) {
    return Uni.createFrom().item(list(keys));
  }

  @Override
  public Uni<V> removeAsync(K key) {
    return Uni.createFrom().item(map.remove(key));
  }

  @Override
  public V put(K key, V value) {
    return map.put(key,value);
  }

  @Override
  public Uni<V> putAsync(K key, V value) {
    return Uni.createFrom().item(put(key, value));
  }

  @Override
  public Uni<Void> putAllAsync(Map<K, V> m) {
    this.map.putAll(m);
    return Uni.createFrom().voidItem();
  }

  @Override
  public Uni<V> persistAsync(V v) {
    return putAsync(keyExtractor.apply(v), v);
  }

  @Override
  public Uni<Void> persistAsync(Collection<V> v) {
    var m = Lists.fixedSize.ofAll(v)
      .groupByUniqueKey(keyExtractor::apply);
    return putAllAsync(m);
  }

  @Override
  public Uni<V> computeAsync(K key, BiFunction<K, V, V> function) {
    return Uni.createFrom().item(map.compute(key, function));
  }



}
