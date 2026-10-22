package pl.gradusiuris.backend.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.gradusiuris.backend.user.dto.MeResponse;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public MeResponse me(Authentication authentication) {
        // Numer konta ustawia JwtAuthenticationFilter (szczegóły uwierzytelnienia).
        Long id = authentication.getDetails() instanceof Long userId ? userId : null;
        return new MeResponse(authentication.getName(), id);
    }
}
