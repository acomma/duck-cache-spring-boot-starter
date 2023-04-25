package me.acomma.duck.boot.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 缓存的配置属性.
 */
@ConfigurationProperties(prefix = DuckCacheProperties.PREFIX)
@Getter
@Setter
public class DuckCacheProperties {
    public static final String PREFIX = "duck.cache";

    /**
     * Redis 缓存的配置属性, {@code KEY} 为缓存的名称.
     */
    private Map<String, CacheProperties.Redis> redis;
}
