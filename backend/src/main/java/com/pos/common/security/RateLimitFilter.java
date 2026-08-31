package com.pos.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pos.common.response.ApiErrorResponse;
import com.pos.common.response.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    
    // Config: 100 requests per minute per IP
    private final int MAX_TOKENS;
    private final long REFILL_RATE_MS = 60000;

    public RateLimitFilter(ObjectMapper objectMapper, @org.springframework.beans.factory.annotation.Value("${app.security.rate-limit.max-tokens:1000}") int maxTokens) {
        this.objectMapper = objectMapper;
        this.MAX_TOKENS = maxTokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
            
        String clientIp = request.getRemoteAddr();
        if (clientIp == null) {
            clientIp = "unknown";
        }
        
        TokenBucket bucket = buckets.computeIfAbsent(clientIp, k -> new TokenBucket(MAX_TOKENS, MAX_TOKENS, System.currentTimeMillis()));
        
        if (!bucket.tryConsume()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiErrorResponse apiError = ApiErrorResponse.of(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.defaultMessage(), null, null);
            response.getWriter().write(objectMapper.writeValueAsString(apiError));
            return;
        }

        filterChain.doFilter(request, response);
    }
    
    private class TokenBucket {
        private final int maxTokens;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefillTime;
        
        TokenBucket(int maxTokens, int initialTokens, long lastRefillTime) {
            this.maxTokens = maxTokens;
            this.tokens = new AtomicInteger(initialTokens);
            this.lastRefillTime = new AtomicLong(lastRefillTime);
        }
        
        boolean tryConsume() {
            refill();
            int currentTokens = tokens.get();
            while (currentTokens > 0) {
                if (tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                    return true;
                }
                currentTokens = tokens.get();
            }
            return false;
        }
        
        private void refill() {
            long now = System.currentTimeMillis();
            long last = lastRefillTime.get();
            long elapsed = now - last;
            if (elapsed > REFILL_RATE_MS) {
                if (lastRefillTime.compareAndSet(last, now)) {
                    tokens.set(maxTokens);
                }
            }
        }
    }
}
