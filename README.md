一种增强 Spring Redis Cache 的方式。

在使用 Redis 作为 [Spring Cache Abstraction](https://docs.spring.io/spring-framework/docs/5.3.27/reference/html/integration.html#cache) 的[实现](https://docs.spring.io/spring-data/redis/docs/2.7.11/reference/html/#redis:support:cache-abstraction)时可以 `spring.cache.redis.*` 对缓存做一些配置，常见的是通过 `spring.cache.redis.time-to-live` 配置缓存的过期时间，这个配置会使所有的缓存都有一样的过期时间，当前的这个实现可以使用 `duck.cache.redis.{cache-name}.time-to-live` 为不同的缓存配置不同的过期时间，举个例子

```yaml
duck:
  cache:
    redis:
      user:
        time-to-live: 2m
      role:
        time-to-live: 5m
```

在这个例子中 `user` 缓存有 2 分钟的过期时间，`role` 缓存有 5 分钟的过期时间。
