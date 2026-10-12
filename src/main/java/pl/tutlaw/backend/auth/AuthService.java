package pl.tutlaw.backend.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.auth.dto.AuthResponse;
import pl.tutlaw.backend.auth.dto.LoginRequest;
import pl.tutlaw.backend.auth.dto.RegisterRequest;
import pl.tutlaw.backend.user.User;
import pl.tutlaw.backend.user.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByPrivateEmail(request.privateEmail())) {
            throw new IllegalArgumentException("Private email already registered");
        }
        if (userRepository.existsByUniversityEmail(request.universityEmail())) {
            throw new IllegalArgumentException("University email already registered");
        }

        User user = new User();
        user.setPrivateEmail(request.privateEmail());
        user.setUniversityEmail(request.universityEmail());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(token);

        log.info("Verification link generated for university email {}: http://localhost:5173/verify?token={}",
                user.getUniversityEmail(), token.getToken());
        emailService.sendVerificationEmail(user.getUniversityEmail(), token.getToken());
    }

    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        User user = token.getUser();

        if (token.getUsedAt() != null) {
            if (user.isActive() && user.isUniversityEmailVerified()) {
                log.info("Token for {} already used previously - returning success (idempotent)",
                        user.getPrivateEmail());
                return;
            }
            throw new IllegalStateException("Token already used");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token expired");
        }


        user.setUniversityEmailVerified(true);
        user.setActive(true);
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        log.info("User {} successfully verified", user.getPrivateEmail());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByPrivateEmail(request.privateEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login attempt for {}", request.privateEmail());
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("Account not verified yet");
        }

        return new AuthResponse(jwtService.generateToken(user.getPrivateEmail(), user.getRole()));
    }
}