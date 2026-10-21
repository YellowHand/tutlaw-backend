package pl.gradusiuris.backend.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    /** Adres nadawcy („Od”). Pusty: nie ustawiamy go, a serwer poczty wstawi swój domyślny (tylko lokalnie, np. Mailpit). */
    private final String from;

    /**
     * @param frontendUrl adres frontendu z konfiguracji ({@code app.frontend-url}, czyli zmienna {@code FRONTEND_URL});
     *                    z niego powstają wszystkie linki w mailach
     * @param from        nadawca ({@code app.mail.from}, zmienna {@code MAIL_FROM}); na produkcji musi to być adres w domenie
     *                    zweryfikowanej u dostawcy poczty (Amazon SES odrzuca inne)
     */
    @Autowired
    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.mail.from:}") String from
    ) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.from = from == null ? "" : from.trim();
    }

    /** Bez nadawcy: dla testów, które nie sprawdzają nagłówka „Od”. */
    public EmailService(JavaMailSender mailSender, String frontendUrl) {
        this(mailSender, frontendUrl, "");
    }

    private SimpleMailMessage newMessage(String toEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isEmpty()) {
            message.setFrom(from);
        }
        message.setTo(toEmail);
        return message;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        SimpleMailMessage message = newMessage(toEmail);
        message.setSubject("Potwierdź rejestrację w Gradus iuris");
        message.setText(
                "Cześć!\n\nKliknij link, aby aktywować konto:\n\n" +
                        frontendUrl + "/verify?token=" + token +
                        "\n\nLink wygasa po 24 godzinach."
        );
        mailSender.send(message);
    }

    /** Link do ustawienia nowego hasła. Wysyłamy go na zweryfikowany adres uczelniany konta. */
    public void sendPasswordResetEmail(String toEmail, String token) {
        SimpleMailMessage message = newMessage(toEmail);
        message.setSubject("Zmiana hasła w Gradus iuris");
        message.setText(
                "Cześć!\n\nOtrzymaliśmy prośbę o zmianę hasła do Twojego konta w Gradus iuris. " +
                        "Kliknij link, aby ustawić nowe hasło:\n\n" +
                        frontendUrl + "/reset-password?token=" + token +
                        "\n\nLink jest ważny przez godzinę i można go użyć tylko raz." +
                        "\n\nJeśli to nie Ty prosiłeś o zmianę hasła, zignoruj tę wiadomość. " +
                        "Twoje hasło pozostanie bez zmian."
        );
        mailSender.send(message);
    }

    /** Powiadomienie, że hasło do konta zostało zmienione (z poziomu konta). Daje szansę zareagować, gdy to nie Ty. */
    public void sendPasswordChangedEmail(String toEmail) {
        SimpleMailMessage message = newMessage(toEmail);
        message.setSubject("Hasło do Twojego konta w Gradus iuris zostało zmienione");
        message.setText(
                "Cześć!\n\nHasło do Twojego konta w Gradus iuris zostało właśnie zmienione, a wszystkie wcześniejsze sesje " +
                        "(zalogowane urządzenia) zostały wylogowane." +
                        "\n\nJeśli to Ty, nic nie musisz robić." +
                        "\n\nJeśli to nie Ty, ustaw nowe hasło od razu tutaj:\n\n" +
                        frontendUrl + "/forgot-password"
        );
        mailSender.send(message);
    }
}
