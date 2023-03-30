package org.hpcclab.oaas.invoker.ispn.store;

import org.hpcclab.oaas.model.HasKey;
import org.infinispan.commons.configuration.BuiltBy;
import org.infinispan.commons.configuration.ConfigurationFor;
import org.infinispan.commons.configuration.attributes.AttributeDefinition;
import org.infinispan.commons.configuration.attributes.AttributeSerializer;
import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.configuration.cache.AbstractStoreConfiguration;
import org.infinispan.configuration.cache.AbstractStoreConfigurationBuilder;
import org.infinispan.configuration.cache.AsyncStoreConfiguration;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ConfigurationFor(ArgCacheStore.class)
@BuiltBy(ArgCacheStoreConfig.Builder.class)
public class ArgCacheStoreConfig extends AbstractStoreConfiguration{
    private static final Logger logger = LoggerFactory.getLogger( ArgCacheStoreConfig.class );

    public static final AttributeDefinition<Class> VALUE_CLASS = AttributeDefinition.builder(
      "valueCls", HasKey.class, Class.class)
            .serializer(AttributeSerializer.CLASS_NAME)
            .immutable().build();

    public ArgCacheStoreConfig(AttributeSet attributes, AsyncStoreConfiguration async ) {
        super(attributes, async);
    }

    public static AttributeSet attributeDefinitionSet() {
        return new AttributeSet(ArgCacheStoreConfig.class, AbstractStoreConfiguration.attributeDefinitionSet(), VALUE_CLASS);
    }

    public Class getValuaCls() {
        return attributes.attribute(VALUE_CLASS)
                .get();
    }

    public static class Builder extends AbstractStoreConfigurationBuilder<ArgCacheStoreConfig, Builder> {
        ArgConnectionFactory connectionFactory;

        public Builder(
                PersistenceConfigurationBuilder builder) {
            super(builder, ArgCacheStoreConfig.attributeDefinitionSet());
        }


        @Override
        public ArgCacheStoreConfig create() {
            return new ArgCacheStoreConfig(
                    super.attributes.protect(),
                    super.async.create()
            );
        }

        @Override
        public Builder self() {
            return this;
        }

        public Builder valueCls(Class klass) {
            attributes.attribute(VALUE_CLASS).set(klass);
            return this;
        }
    }
}
