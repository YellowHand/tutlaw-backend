package pl.tutlaw.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pl.tutlaw.backend.common.exception.UnauthorizedException;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    /**
     * Zwraca zalogowanego użytkownika. Brak logowania albo użytkownik, którego już nie ma w bazie
     * (token jeszcze ważny), to 401, a nie 409 jak przy dawnym IllegalStateException.
     */
    public User resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Wymagane zalogowanie");
        }
        return userRepository.findByPrivateEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Konto użytkownika nie istnieje"));
    }
}
