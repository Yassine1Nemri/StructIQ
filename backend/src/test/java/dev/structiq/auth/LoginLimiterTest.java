package dev.structiq.auth;

import java.time.*;
import org.junit.jupiter.api.Test;
import dev.structiq.common.ApiException;
import static org.assertj.core.api.Assertions.*;

class LoginLimiterTest {
    @Test void rateLimitIsPerAddressAndWindowsExpire() {
        class MutableClock extends Clock {
            Instant now=Instant.parse("2026-01-01T00:00:00Z");
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now; }
        }
        var clock=new MutableClock();
        var limiter=new LoginLimiter(clock);
        for(int n=0;n<10;n++) limiter.check("a");
        assertThatThrownBy(() -> limiter.check("a")).isInstanceOfSatisfying(ApiException.class,
            ex -> assertThat(ex.status).isEqualTo(429));
        assertThatCode(() -> limiter.check("b")).doesNotThrowAnyException();
        clock.now=clock.now.plusSeconds(60);
        assertThatCode(() -> limiter.check("a")).doesNotThrowAnyException();
    }
}
