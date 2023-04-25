package me.acomma.duck.boot.cache;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.cache.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheAspectSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 缓存的自动配置. 部分代码拷贝自 {@link org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(CacheManager.class)
@ConditionalOnBean(CacheAspectSupport.class)
@ConditionalOnMissingBean(value = CacheManager.class, name = "cacheResolver")
@EnableConfigurationProperties(CacheProperties.class)
@AutoConfigureAfter({
        CouchbaseDataAutoConfiguration.class,
        HazelcastAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        RedisAutoConfiguration.class})
public class DuckCacheAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public CacheManagerCustomizers cacheManagerCustomizers(ObjectProvider<CacheManagerCustomizer<?>> customizers) {
        return new CacheManagerCustomizers(customizers.orderedStream().collect(Collectors.toList()));
    }

    @Bean
    public CacheManagerValidator cacheAutoConfigurationValidator(CacheProperties cacheProperties,
                                                                 ObjectProvider<CacheManager> cacheManager) {
        return new CacheManagerValidator(cacheProperties, cacheManager);
    }

    /**
     * Redis 缓存配置. 部分代码拷贝自 {@link org.springframework.boot.autoconfigure.cache.RedisCacheConfiguration}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisConnectionFactory.class)
    @AutoConfigureAfter(RedisAutoConfiguration.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(CacheManager.class)
    @EnableConfigurationProperties(value = {CacheProperties.class, DuckCacheProperties.class})
    static class DuckRedisCacheConfiguration {
        @Bean
        RedisCacheManager cacheManager(CacheProperties cacheProperties,
                                       CacheManagerCustomizers cacheManagerCustomizers,
                                       ObjectProvider<RedisCacheConfiguration> redisCacheConfiguration,
                                       ObjectProvider<RedisCacheManagerBuilderCustomizer> redisCacheManagerBuilderCustomizers,
                                       RedisConnectionFactory redisConnectionFactory,
                                       ResourceLoader resourceLoader,
                                       DuckCacheProperties duckCacheProperties) {
            RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(redisConnectionFactory)
                    .cacheDefaults(determineConfiguration(cacheProperties, redisCacheConfiguration, resourceLoader.getClassLoader()));
            List<String> cacheNames = cacheProperties.getCacheNames();
            if (!cacheNames.isEmpty()) {
                builder.initialCacheNames(new LinkedHashSet<>(cacheNames));
            }
            if (cacheProperties.getRedis().isEnableStatistics()) {
                builder.enableStatistics();
            }
            // 关键在这里, 附加要预初始化的缓存名称 / RedisCacheConfiguration 对的映射
            if (!duckCacheProperties.getRedis().isEmpty()) {
                builder.withInitialCacheConfigurations(createConfiguration(duckCacheProperties, resourceLoader.getClassLoader()));
            }
            redisCacheManagerBuilderCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
            return cacheManagerCustomizers.customize(builder.build());
        }

        private org.springframework.data.redis.cache.RedisCacheConfiguration determineConfiguration(
                CacheProperties cacheProperties,
                ObjectProvider<org.springframework.data.redis.cache.RedisCacheConfiguration> redisCacheConfiguration,
                ClassLoader classLoader) {
            return redisCacheConfiguration.getIfAvailable(() -> createConfiguration(cacheProperties, classLoader));
        }

        private org.springframework.data.redis.cache.RedisCacheConfiguration createConfiguration(
                CacheProperties cacheProperties, ClassLoader classLoader) {
            CacheProperties.Redis redisProperties = cacheProperties.getRedis();
            return createConfiguration(redisProperties, classLoader);
        }

        private Map<String, org.springframework.data.redis.cache.RedisCacheConfiguration> createConfiguration(
                DuckCacheProperties duckCacheProperties, ClassLoader classLoader) {
            Map<String, org.springframework.data.redis.cache.RedisCacheConfiguration> configs = new LinkedHashMap<>(duckCacheProperties.getRedis().size());
            for (Map.Entry<String, CacheProperties.Redis> entry : duckCacheProperties.getRedis().entrySet()) {
                CacheProperties.Redis redisProperties = entry.getValue();
                configs.put(entry.getKey(), createConfiguration(redisProperties, classLoader));
            }
            return configs;
        }

        private org.springframework.data.redis.cache.RedisCacheConfiguration createConfiguration(CacheProperties.Redis redisProperties, ClassLoader classLoader) {
            org.springframework.data.redis.cache.RedisCacheConfiguration config = org.springframework.data.redis.cache.RedisCacheConfiguration
                    .defaultCacheConfig();
            config = config.serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(new JdkSerializationRedisSerializer(classLoader)));
            if (redisProperties.getTimeToLive() != null) {
                config = config.entryTtl(redisProperties.getTimeToLive());
            }
            if (redisProperties.getKeyPrefix() != null) {
                config = config.prefixCacheNameWith(redisProperties.getKeyPrefix());
            }
            if (!redisProperties.isCacheNullValues()) {
                config = config.disableCachingNullValues();
            }
            if (!redisProperties.isUseKeyPrefix()) {
                config = config.disableKeyPrefix();
            }
            return config;
        }
    }

    /**
     * Bean used to validate that a CacheManager exists and provide a more meaningful
     * exception.
     */
    static class CacheManagerValidator implements InitializingBean {
        private final CacheProperties cacheProperties;

        private final ObjectProvider<CacheManager> cacheManager;

        CacheManagerValidator(CacheProperties cacheProperties, ObjectProvider<CacheManager> cacheManager) {
            this.cacheProperties = cacheProperties;
            this.cacheManager = cacheManager;
        }

        @Override
        public void afterPropertiesSet() {
            Assert.notNull(this.cacheManager.getIfAvailable(),
                    () -> "No cache manager could be auto-configured, check your configuration (caching type is '"
                            + this.cacheProperties.getType() + "')");
        }
    }
}
