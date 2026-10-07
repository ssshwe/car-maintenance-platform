package com.example.maintenance.auth;

import com.example.maintenance.common.BusinessException;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiter {
    private record Window(long started, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    public void check(String key, int maximum) {
        long now = System.currentTimeMillis();
        if (windows.size() > 5000) windows.entrySet().removeIf(x -> now - x.getValue().started > 60_000);
        Window window = windows.compute(key, (k, old) -> old == null || now - old.started > 60_000 ? new Window(now, 1) : new Window(old.started, old.count + 1));
        if (window.count > maximum) throw new BusinessException("RATE_LIMITED", "操作过于频繁，请一分钟后重试", 429);
    }
}
