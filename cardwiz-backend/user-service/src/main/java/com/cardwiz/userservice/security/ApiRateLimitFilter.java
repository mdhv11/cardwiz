package com.cardwiz.userservice.security;

import com.cardwiz.userservice.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            return false;
        }
        return path.startsWith("/actuator/health")
                || path.startsWith("/actuator/info")
                || path.startsWith("/api/v1/cards/internal/ingestion-callback");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        LimitPolicy policy = resolvePolicy(request.getRequestURI());
        String actor = resolveActorKey(request);
        String redisKey = "rate:user-service:" + policy.keyPrefix() + ":" + actor;

        try {
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);
            if (currentCount != null && currentCount == 1L) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(policy.windowSeconds()));
            }

            long count = currentCount == null ? 1L : currentCount;
            long remaining = Math.max(0L, policy.limit() - count);
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            long retryAfter = ttl == null || ttl < 0 ? policy.windowSeconds() : ttl;

            response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            response.setHeader("X-RateLimit-Window", String.valueOf(policy.windowSeconds()));

            if (count > policy.limit()) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        "{\"message\":\"Too many requests. Please retry shortly.\",\"retryAfterSeconds\":" + retryAfter + "}"
                );
                return;
            }
        } catch (Exception ex) {
            // Fail open to avoid taking down APIs if Redis is unstable.
            log.warn("Rate limit check failed; allowing request. path={}", request.getRequestURI(), ex);
        }

        filterChain.doFilter(request, response);
    }

    private LimitPolicy resolvePolicy(String path) {
        if (path == null) {
            return new LimitPolicy("default", properties.getDefaultLimit(), properties.getDefaultWindowSeconds());
        }
        if (path.startsWith("/api/v1/auth/")) {
            return new LimitPolicy("auth", properties.getAuthLimit(), properties.getAuthWindowSeconds());
        }
        if (path.startsWith("/api/v1/cards/recommendations")
                || path.startsWith("/api/v1/cards/statement-missed-savings")
                || path.startsWith("/api/v1/cards/documents/analyze")
                || path.contains("/documents/analyze")
                || path.startsWith("/api/v1/transactions/validate")) {
            return new LimitPolicy("expensive", properties.getExpensiveLimit(), properties.getExpensiveWindowSeconds());
        }
        return new LimitPolicy("default", properties.getDefaultLimit(), properties.getDefaultWindowSeconds());
    }

    private String resolveActorKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getName() != null
                && !authentication.getName().isBlank()) {
            return "user:" + authentication.getName().toLowerCase();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private record LimitPolicy(String keyPrefix, int limit, int windowSeconds) {
    }
}
