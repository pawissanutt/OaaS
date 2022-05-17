package org.hpcclab.oaas.repository;

import io.smallrye.mutiny.Uni;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public interface EntityRepository<K, V> {
  V get(K key);

  Uni<V> getAsync(K key);

  Map<K, V> list(Set<K> keys);

  Uni<Map<K, V>> listAsync(Set<K> keys);

  default Uni<List<V>> orderedListAsync(List<K> keys) {
    if (keys==null || keys.isEmpty()) return Uni.createFrom().item(List.of());
    return this.listAsync(Set.copyOf(keys))
      .map(map -> keys.stream()
        .map(id -> {
          var v = map.get(id);
          if (v==null) throw new IllegalStateException();
          return v;
        })
        .toList()
      );
  }

  Uni<V> removeAsync(K key);

  V put(K key, V value);

  Uni<V> putAsync(K key, V value);
  Uni<Void> putAllAsync(Map<K, V> map);

  Uni<V> persistAsync(V v);

  Uni<Void> persistAsync(Collection<V> v);

  Uni<V> computeAsync(K key, BiFunction<K, V, V> function);
}
