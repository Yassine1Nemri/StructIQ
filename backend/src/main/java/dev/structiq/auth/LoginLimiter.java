package dev.structiq.auth;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import dev.structiq.common.ApiException;

/** Single-instance fixed-window limiter; ignores spoofable forwarded headers. */
@Component
public class LoginLimiter {
    private record Window(long minute, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public LoginLimiter() { this(Clock.systemUTC()); }
    LoginLimiter(Clock clock) { this.clock = clock; }
    public synchronized void check(String address) {
        long minute = clock.millis() / 60_000;
        windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        var window = windows.getOrDefault(address, new Window(minute, 0));
        if (window.count() >= 10 || (!windows.containsKey(address) && windows.size() >= 10_000)) {
            throw new ApiException(429, "RATE_LIMITED", "Too many login attempts; try again in a minute");
        }
        windows.put(address, new Window(minute, window.count() + 1));
    }
}
