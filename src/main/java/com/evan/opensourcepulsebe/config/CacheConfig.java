package com.evan.opensourcepulsebe.config;

import com.evan.opensourcepulsebe.common.dto.PagedResponse;
import com.evan.opensourcepulsebe.issue.dto.IssueSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    RedisCacheManagerBuilderCustomizer redisCacheCustomizer(RedisConnectionFactory connectionFactory,
                                                            JsonMapper jsonMapper) {
        JavaType issuePage = jsonMapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, IssueSummaryResponse.class);
        JavaType issue = jsonMapper.getTypeFactory().constructType(IssueSummaryResponse.class);

        return builder -> {
            RedisCacheConfiguration defaults = builder.cacheDefaults();
            builder
                    .cacheWriter(RedisCacheWriter.nonLockingRedisCacheWriter(
                            connectionFactory, BatchStrategies.scan(1000)))
                    .transactionAware()
                    .disableCreateOnMissingCache()
                    .withCacheConfiguration(CacheNames.ISSUE_SEARCH,
                            defaults.serializeValuesWith(json(jsonMapper, issuePage)))
                    .withCacheConfiguration(CacheNames.ISSUE_BY_ID,
                            defaults.serializeValuesWith(json(jsonMapper, issue)));
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache GET failed [cache={}, key={}]: {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed [cache={}, key={}]: {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache EVICT failed [cache={}, key={}]: {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.error("Cache CLEAR failed [cache={}], stale entries remain until TTL expiry: {}",
                        cache.getName(), ex.getMessage());
            }
        };
    }

    private static SerializationPair<Object> json(JsonMapper jsonMapper, JavaType type) {
        return SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(jsonMapper, type));
    }
}
