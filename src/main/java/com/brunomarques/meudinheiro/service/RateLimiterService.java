package com.brunomarques.meudinheiro.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<String, Bucket> aiCache = new ConcurrentHashMap<>();
    private final Map<String, Bucket> dbCache = new ConcurrentHashMap<>();

    // Regra para IA: Máximo 3 requisições. Recarrega 3 a cada 1 minuto.
    public boolean tryConsumeAi(String userId) {
        return aiCache.computeIfAbsent(userId, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(1))))
                .build()).tryConsume(1);
    }

    // Regra para Manual: Máximo 20 requisições. Recarrega 20 a cada 1 minuto.
    // (Ninguém digita mais de 20 despesas por minuto na mão de forma legítima)
    public boolean tryConsumeManual(String userId) {
        return dbCache.computeIfAbsent(userId, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(20, Refill.intervally(20, Duration.ofMinutes(1))))
                .build()).tryConsume(1);
    }
}