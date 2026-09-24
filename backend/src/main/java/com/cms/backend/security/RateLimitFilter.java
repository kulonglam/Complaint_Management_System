package com.cms.backend.security;

import com.cms.backend.client.SupabaseAdminClient;
import com.cms.backend.config.AppProperties;
import com.cms.backend.config.SupabaseProperties;
import com.cms.backend.exception.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties appProperties;
    private final SupabaseProperties supabaseProperties;
    private final ObjectProvider<SupabaseAdminClient> supabaseAdminClient;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            AppProperties appProperties,
            SupabaseProperties supabaseProperties,
            ObjectProvider<SupabaseAdminClient> supabaseAdminClient
    ) {
        this.appProperties = appProperties;
        this.supabaseProperties = supabaseProperties;
        this.supabaseAdminClient = supabaseAdminClient;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        if (!allow(key)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many requests. Please try again shortly.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    boolean allow(String key) {
        SupabaseAdminClient client = supabaseAdminClient.getIfAvailable();
        if (supabaseProperties.configured() && client != null) {
            try {
                client.assertNamedRateLimit("api", key, Math.max(appProperties.rateLimitCapacity(), 1));
                return true;
            } catch (ApiException ex) {
                if (ex.getStatus() == HttpStatus.BAD_REQUEST) {
                    return false;
                }
            } catch (RuntimeException ignored) {
                // fall through to the local window
            }
        }
        return allowLocal(key);
    }

    private boolean allowLocal(String key) {
        long now = Instant.now().getEpochSecond();
        long window = Math.max(appProperties.rateLimitWindowSeconds(), 1);
        long bucket = now / window;
        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.bucket != bucket) {
                return new Window(bucket, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return current.count.get() <= Math.max(appProperties.rateLimitCapacity(), 1);
    }

    static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private record Window(long bucket, AtomicInteger count) {
    }
}
