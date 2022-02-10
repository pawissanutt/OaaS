package org.hpcclab.oaas.storage;

import io.quarkus.runtime.StartupEvent;
import org.hpcclab.oaas.repository.init.InfinispanInit;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class ServerInitializer {

  @Inject
  InfinispanInit infinispanInit;


  void onStart(@Observes StartupEvent startupEvent) {
    infinispanInit.setup();
  }

}
