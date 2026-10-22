package pl.gradusiuris.backend.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Podpina sprawdzanie Turnstile pod endpointy, które boty lubią: tworzenie konta, logowanie i wysyłanie maili. */
@Configuration
public class TurnstileConfig implements WebMvcConfigurer {

    static final String[] PROTECTED_PATHS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/resend-verification",
    };

    private final TurnstileVerifier verifier;

    public TurnstileConfig(TurnstileVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TurnstileInterceptor(verifier)).addPathPatterns(PROTECTED_PATHS);
    }
}
