package com.cardwiz.userservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {
    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);
    // Changing this prefix forces the app to ignore old broken cache keys
    private static final String CACHE_KEY_PREFIX_VERSION = "v4";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        // Use our custom safe serializer
        RedisSerializer<Object> valueSerializer = new SafeRedisValueSerializer();

        RedisCacheConfiguration defaultCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .computePrefixWith(cacheName -> CACHE_KEY_PREFIX_VERSION + "::" + cacheName + "::")
                .entryTtl(Duration.ofMinutes(60))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));

        return builder -> builder
                .cacheDefaults(defaultCacheConfiguration)
                .withCacheConfiguration("userProfileByIdV2", defaultCacheConfiguration.entryTtl(Duration.ofMinutes(20)))
                .withCacheConfiguration("userProfileByEmailV2", defaultCacheConfiguration.entryTtl(Duration.ofMinutes(20)))
                .withCacheConfiguration("cardMetadataByUserV2", defaultCacheConfiguration.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("cardMetadataByIdV2", defaultCacheConfiguration.entryTtl(Duration.ofMinutes(30)))
                .withCacheConfiguration("aiRecommendationsV2", defaultCacheConfiguration.entryTtl(Duration.ofMinutes(10)));
    }

    @Bean
    public SimpleKeyGenerator keyGenerator() {
        return new SimpleKeyGenerator();
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis GET failed for key='{}'. Treating as Cache Miss.", key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed for key='{}'.", key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis EVICT failed for key='{}'.", key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis CLEAR failed.", exception);
            }
        };
    }

    /**
     * Custom Serializer that handles Lists/Generics correctly and prevents crashes on bad data.
     */
    static class SafeRedisValueSerializer implements RedisSerializer<Object> {
        private final GenericJackson2JsonRedisSerializer delegate;
        private final ObjectMapper fallbackMapper;

        public SafeRedisValueSerializer() {
            ObjectMapper objectMapper = new ObjectMapper();

            // 1. Handle Java 8 Time (LocalDateTime, etc.)
            objectMapper.registerModule(new JavaTimeModule());

            // 2. Prevent crashes if you add new fields to DTOs in the future
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 3. ENABLE TYPE INFO: This tells Redis "This array is actually a List of UserCardResponse"
            objectMapper.activateDefaultTyping(
                    BasicPolymorphicTypeValidator.builder()
                            .allowIfBaseType(Object.class)
                            .build(),
                    ObjectMapper.DefaultTyping.NON_FINAL,
                    JsonTypeInfo.As.PROPERTY
            );

            this.delegate = new GenericJackson2JsonRedisSerializer(objectMapper);
            this.fallbackMapper = objectMapper;
        }

        @Override
        public byte[] serialize(Object value) {
            try {
                return delegate.serialize(value);
            } catch (Exception e) {
                log.error("Serialization failed for value: {}", value, e);
                return null;
            }
        }

        @Override
        public Object deserialize(byte[] bytes) {
            try {
                return delegate.deserialize(bytes);
            } catch (Exception ex) {
                // Backward compatibility: old entries may be arrays of typed objects without root type metadata.
                Object recovered = tryRecoverLegacyPayload(bytes);
                if (recovered != null) {
                    return recovered;
                }
                // If payload is truly unreadable, treat as cache miss.
                log.warn("Unreadable Redis payload found. Treating as Cache Miss. Error: {}", ex.getMessage());
                return null;
            }
        }

        private Object tryRecoverLegacyPayload(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            try {
                JsonNode root = fallbackMapper.readTree(bytes);
                if (root == null || root.isNull()) {
                    return null;
                }
                return recoverNode(root);
            } catch (Exception ignored) {
                return null;
            }
        }

        private Object recoverNode(JsonNode node) throws Exception {
            if (node == null || node.isNull()) {
                return null;
            }

            if (node.isArray()) {
                List<Object> values = new ArrayList<>();
                for (JsonNode child : node) {
                    values.add(recoverNode(child));
                }
                return values;
            }

            if (node.isObject() && node.has("@class") && node.get("@class").isTextual()) {
                String className = node.get("@class").asText();
                Class<?> targetClass = Class.forName(className);
                JsonNode copy = node.deepCopy();
                ((ObjectNode) copy).remove("@class");
                return fallbackMapper.treeToValue(copy, targetClass);
            }

            return fallbackMapper.treeToValue(node, Object.class);
        }
    }
}
