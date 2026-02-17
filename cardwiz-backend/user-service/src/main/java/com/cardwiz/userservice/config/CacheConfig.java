package com.cardwiz.userservice.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        RedisCacheConfiguration defaultCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())
                );

        return builder -> builder
                .cacheDefaults(defaultCacheConfiguration)
                .withCacheConfiguration(
                        "userProfileById",
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(20))
                )
                .withCacheConfiguration(
                        "userProfileByEmail",
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(20))
                )
                .withCacheConfiguration(
                        "cardMetadataByUser",
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(30))
                )
                .withCacheConfiguration(
                        "cardMetadataById",
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(30))
                )
                .withCacheConfiguration(
                        "aiRecommendations",
                        defaultCacheConfiguration.entryTtl(Duration.ofMinutes(10))
                );
    }

    @Bean
    public SimpleKeyGenerator keyGenerator() {
        return new SimpleKeyGenerator();
    }
}
