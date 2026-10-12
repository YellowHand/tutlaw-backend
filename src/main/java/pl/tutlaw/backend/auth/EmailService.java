package pl.tutlaw.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

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
}