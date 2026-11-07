package dev.structiq.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import dev.structiq.common.ApiException;
import dev.structiq.user.UserRepository;
import dev.structiq.user.UserRepository.User;
import dev.structiq.user.UserRepository.UserView;

@Service
public class AuthService {
    public record Login(@NotBlank @Email @Size(max=254) String email,
                        @NotBlank @Size(max=72) String password) {}
    public record Session(String accessToken, String tokenType, long expiresIn, UserView user) {}
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final LoginLimiter limiter;
    private final String dummyHash;
    public AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder, LoginLimiter limiter) {
        this.users = users; this.passwords = passwords; this.encoder = encoder; this.limiter = limiter;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }
    public Session login(Login input, String remoteAddress) {
        limiter.check(remoteAddress);
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ApiException(401, "INVALID_CREDENTIALS", "Invalid email or password");
        var found = users.byEmail(input.email().strip().toLowerCase(Locale.ROOT));
        boolean matches = passwords.matches(input.password(), found.map(User::passwordHash).orElse(dummyHash));
        if (found.isEmpty() || !matches) throw new ApiException(401, "INVALID_CREDENTIALS", "Invalid email or password");
        var user = found.get();
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("structiq").audience(List.of("structiq-web"))
            .subject(user.id().toString()).issuedAt(now).expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .claim("role", user.globalRole()).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Session(token, "Bearer", 900, user.view());
    }
    public User current(Jwt jwt) {
        return users.byId(UUID.fromString(jwt.getSubject()))
            .orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "Authentication required"));
    }
}
