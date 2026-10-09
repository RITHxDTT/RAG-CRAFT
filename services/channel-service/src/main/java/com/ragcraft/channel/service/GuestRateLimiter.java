package com.ragcraft.channel.service;

import com.ragcraft.channel.ChannelProperties;
import com.ragcraft.common.web.AppException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** In-process sliding-minute limiter for the public ask endpoint (30 per minute per IP by default). */
@Component
public class GuestRateLimiter {

    private record Window(long minute, int count) {}

    private final ChannelProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public GuestRateLimiter(ChannelProperties properties) {
        this.properties = properties;
    }

    public void check(String key) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(key, (k, current) -> current == null || current.minute() != minute
                ? new Window(minute, 1) : new Window(minute, current.count() + 1));
        if (window.count() > properties.getGuestRateLimitPerMinute()) {
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS, "Too many messages. Please wait a moment and try again.");
        }
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> entry.getValue().minute() != minute);
    }
}
