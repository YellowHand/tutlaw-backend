package pl.gradusiuris.backend.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pl.gradusiuris.backend.auth.dto.RegisterRequest;
import pl.gradusiuris.backend.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailVerificationTokenRepository tokenRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerForAnExistingAddressIsSilentlyIgnored() {
        RegisterRequest request = new RegisterRequest(
                "existing@gmail.com", "existing@studms.ug.edu.pl", "password123", true);

        when(userRepository.existsByPrivateEmailIgnoreCase("existing@gmail.com")).thenReturn(true);

        // Odpowiedź ma być taka sama jak dla nowego konta: żadnego błędu, który zdradzałby, że konto istnieje.
        assertThatCode(() -> authService.register(request)).doesNotThrowAnyException();
        verify(userRepository, never()).save(any());
    }
}