package org.hpcclab.oaas.repository;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;

public interface EntityRepository<K, V> {

  V get(K key);


  Map<K, V> list(Collection<K> keys);


  V remove(K key);


  default void delete(K key) {
    remove(key);
  }

  V put(K key, V value);

  V persist(V v);

  default void persist(Collection<V> collection) {
    for (V v : collection) {
      persist(v);
    }
  }

  V compute(K key, BiFunction<K, V, V> function);

  default QueryService<K, V> getQueryService() {
    throw new UnsupportedOperationException();
  }

  default AtomicOperationService<K, V> atomic() {
    throw new UnsupportedOperationException();
  }

  default AsyncEntityRepository<K, V> async() {
    throw new UnsupportedOperationException();
  }

}
