package pl.tutlaw.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String frontendUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.mailSender = mailSender;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Potwierdź rejestrację w Tutlaw");
        message.setText(
                "Cześć!\n\nKliknij link, aby aktywować konto:\n\n" +
                        "http://localhost:5173/verify?token=" + token +
                        "\n\nLink wygasa po 24 godzinach."
        );
        mailSender.send(message);
    }

    /** Link do ustawienia nowego hasła. Wysyłamy go na zweryfikowany adres uczelniany konta. */
    public void sendPasswordResetEmail(String toEmail, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Zmiana hasła w Tutlaw");
        message.setText(
                "Cześć!\n\nOtrzymaliśmy prośbę o zmianę hasła do Twojego konta w Tutlaw. " +
                        "Kliknij link, aby ustawić nowe hasło:\n\n" +
                        frontendUrl + "/reset-password?token=" + token +
                        "\n\nLink jest ważny przez godzinę i można go użyć tylko raz." +
                        "\n\nJeśli to nie Ty prosiłeś o zmianę hasła, zignoruj tę wiadomość. " +
                        "Twoje hasło pozostanie bez zmian."
        );
        mailSender.send(message);
    }
}
