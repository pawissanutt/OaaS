package org.hpcclab.oaas.repository.init;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class InfinispanInit {
  private static final Logger LOGGER = LoggerFactory.getLogger(InfinispanInit.class);
  // language=xml
  private static final String TEMPLATE_MEM_DIST_CONFIG = """
    <distributed-cache name="%s"
                       statistics="true"
                       mode="ASYNC">
      <memory storage="OFF_HEAP"
              max-size="%s"/>
      <encoding>
          <key media-type="application/x-protostream"/>
          <value media-type="application/x-protostream"/>
      </encoding>
      <partition-handling when-split="ALLOW_READ_WRITES"
                          merge-policy="PREFERRED_NON_NULL"/>
      <state-transfer timeout='300000'/>
    </distributed-cache>
    """;

  // language=xml
  private static final String TEMPLATE_DIST_CONFIG = """
    <distributed-cache name="%s"
                       statistics="true"
                       mode="SYNC">
      <memory storage="OFF_HEAP"
              max-size="%s"/>
      <encoding>
          <key media-type="application/x-protostream"/>
          <value media-type="application/x-protostream"/>
      </encoding>
      <persistence passivation="false">
        <file-store shared="false"
                    fetch-state="true"
                    purge="false"
                    preload="false">
          <!--<write-behind modification-queue-size="65536" />-->
        </file-store>
        <!--<rocksdb-store xmlns="urn:infinispan:config:store:rocksdb:13.0"
                       fetch-state="true"/> -->
      </persistence>
      <partition-handling when-split="ALLOW_READ_WRITES"
                          merge-policy="PREFERRED_NON_NULL"/>
      <state-transfer timeout='300000'/>
    </distributed-cache>
    """;
  // language=xml
  private static final String TEMPLATE_REP_CONFIG = """
    <replicated-cache name="%s"
                      statistics="true"
                      mode="SYNC">
      <memory storage="HEAP"
              max-size="%s"/>
      <encoding>
          <key media-type="application/x-protostream"/>
          <value media-type="application/x-protostream"/>
      </encoding>
      <persistence passivation="false">
          <file-store shared="false"
                      fetch-state="true"
                      purge="false"
                      preload="false">
          </file-store>
      </persistence>
      <partition-handling when-split="ALLOW_READ_WRITES"
                          merge-policy="PREFERRED_NON_NULL"/>
      <state-transfer timeout='300000'/>
    </replicated-cache>
    """;
  // language=xml
  private static final String TEMPLATE_TX_CONFIG = """
   <distributed-cache name="%s"
                      statistics="true"
                      mode="SYNC">
     <memory storage="OFF_HEAP"
             max-size="%s"/>
     <locking isolation="REPEATABLE_READ"/>
     <transaction mode="NON_XA"
                  locking="PESSIMISTIC"/>
      <!--  locking="PESSIMISTIC"-->
      <!--  locking="OPTIMISTIC"-->
     <encoding>
         <key media-type="application/x-protostream"/>
         <value media-type="application/x-protostream"/>
     </encoding>
     <persistence passivation="false">
         <file-store shared="false"
                     fetch-state="true"
                     purge="false"
                     preload="false">
         <!--<write-behind modification-queue-size="65536" />-->
         </file-store>
     </persistence>
     <partition-handling when-split="ALLOW_READ_WRITES"
                         merge-policy="PREFERRED_NON_NULL"/>
     <state-transfer timeout='300000'/>
   </distributed-cache>
    """;

  @Inject
  RemoteCacheManager remoteCacheManager;
  @Inject
  RepositoryConfig repositoryConfig;

  public void setup() {
    if (remoteCacheManager==null) {
      throw new RuntimeException("Cannot connect to infinispan cluster");
    }
    var objectCacheConfig = repositoryConfig.object();
    var completionCacheConfig = repositoryConfig.completion();
    var stateCacheConfig = repositoryConfig.completion();
    remoteCacheManager.getConfiguration()
      .addRemoteCache("OaasObject", c -> {
        if (objectCacheConfig.nearCacheMaxEntry() > 0) {
          c.nearCacheMode(NearCacheMode.INVALIDATED)
            .nearCacheMaxEntries(objectCacheConfig.nearCacheMaxEntry());
        }
        c.forceReturnValues(false);
      });
    remoteCacheManager.getConfiguration()
      .addRemoteCache("OaasClass", c -> {
        c.nearCacheMode(NearCacheMode.INVALIDATED)
          .nearCacheMaxEntries(500);
      });
    remoteCacheManager.getConfiguration()
      .addRemoteCache("OaasFunction", c -> {
        c.nearCacheMode(NearCacheMode.INVALIDATED)
          .nearCacheMaxEntries(500);
      });
    remoteCacheManager.getConfiguration()
      .addRemoteCache("TaskCompletion", c -> {
        if (completionCacheConfig.nearCacheMaxEntry() > 0) {
          c.nearCacheMode(NearCacheMode.INVALIDATED)
            .nearCacheMaxEntries(completionCacheConfig.nearCacheMaxEntry());
        }
        c.forceReturnValues(false);
      });
    remoteCacheManager.getConfiguration()
      .addRemoteCache("TaskState", c -> {
        if (stateCacheConfig.nearCacheMaxEntry() > 0) {
          c.nearCacheMode(NearCacheMode.INVALIDATED)
            .nearCacheMaxEntries(stateCacheConfig.nearCacheMaxEntry());
        }
        c.forceReturnValues(false);
      });

    var distTemplate = objectCacheConfig.persist() ?
      TEMPLATE_DIST_CONFIG:TEMPLATE_MEM_DIST_CONFIG;
    remoteCacheManager.administration().getOrCreateCache("OaasObject", new XMLStringConfiguration(distTemplate
      .formatted("OaasObject", objectCacheConfig.maxSize())));
    remoteCacheManager.administration().getOrCreateCache("OaasClass", new XMLStringConfiguration(TEMPLATE_REP_CONFIG
      .formatted("OaasClass", "16MB")));
    remoteCacheManager.administration().getOrCreateCache("OaasFunction", new XMLStringConfiguration(TEMPLATE_REP_CONFIG
      .formatted("OaasFunction", "16MB")));

    distTemplate = completionCacheConfig.persist() ?
      TEMPLATE_DIST_CONFIG:TEMPLATE_MEM_DIST_CONFIG;
    remoteCacheManager.administration().getOrCreateCache("TaskCompletion", new XMLStringConfiguration(distTemplate
      .formatted("TaskCompletion", completionCacheConfig.maxSize())));

    distTemplate = stateCacheConfig.persist() ?
      TEMPLATE_DIST_CONFIG:TEMPLATE_MEM_DIST_CONFIG;
    remoteCacheManager.administration().getOrCreateCache("TaskState", new XMLStringConfiguration(distTemplate
      .formatted("TaskState", completionCacheConfig.maxSize())));


  }
}