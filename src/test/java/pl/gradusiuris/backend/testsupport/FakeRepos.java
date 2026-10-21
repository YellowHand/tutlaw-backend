package pl.gradusiuris.backend.testsupport;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import pl.gradusiuris.backend.auth.EmailVerificationToken;
import pl.gradusiuris.backend.auth.EmailVerificationTokenRepository;
import pl.gradusiuris.backend.auth.PasswordResetToken;
import pl.gradusiuris.backend.auth.PasswordResetTokenRepository;
import pl.gradusiuris.backend.auth.RefreshToken;
import pl.gradusiuris.backend.auth.RefreshTokenRepository;
import pl.gradusiuris.backend.user.User;
import pl.gradusiuris.backend.user.UserRepository;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repozytoria i poczta "w pamięci" do testów logiki kont bez bazy i bez Springa.
 * Metody, których test nie przewidział, kończą się wyjątkiem, więc nowe zapytanie w serwisie nie przejdzie niezauważone.
 */
public final class FakeRepos {

    private FakeRepos() {
    }

    public interface Handler {
        Object handle(String method, Object[] args) throws Throwable;
    }

    /** Atrapa dowolnego interfejsu: każde wywołanie trafia do {@code handler}, a toString/equals/hashCode działają same. */
    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
            switch (method.getName()) {
                case "toString":
                    return type.getSimpleName() + "Fake";
                case "hashCode":
                    return System.identityHashCode(self);
                case "equals":
                    return self == args[0];
                default:
                    return handler.handle(method.getName(), args == null ? new Object[0] : args);
            }
        });
    }

    public static UserRepository users(List<User> store) {
        AtomicLong ids = new AtomicLong(1);
        return proxy(UserRepository.class, (method, args) -> {
            switch (method) {
                case "save": {
                    User user = (User) args[0];
                    if (user.getId() == null) {
                        user.setId(ids.getAndIncrement());
                    }
                    if (!store.contains(user)) {
                        store.add(user);
                    }
                    return user;
                }
                case "findFirstByPrivateEmailIgnoreCaseOrderByIdAsc":
                    return store.stream()
                            .filter(u -> u.getPrivateEmail().equalsIgnoreCase((String) args[0]))
                            .min(Comparator.comparing(User::getId));
                case "existsByPrivateEmailIgnoreCase":
                    return store.stream().anyMatch(u -> u.getPrivateEmail().equalsIgnoreCase((String) args[0]));
                case "existsByUniversityEmailIgnoreCase":
                    return store.stream().anyMatch(u -> u.getUniversityEmail().equalsIgnoreCase((String) args[0]));
                case "findByPrivateEmail":
                    return store.stream().filter(u -> u.getPrivateEmail().equals(args[0])).findFirst();
                case "delete":
                    store.remove(args[0]);
                    return null;
                default:
                    throw new UnsupportedOperationException("UserRepository." + method);
            }
        });
    }

    public static EmailVerificationTokenRepository verificationTokens(List<EmailVerificationToken> store) {
        AtomicLong ids = new AtomicLong(1);
        return proxy(EmailVerificationTokenRepository.class, (method, args) -> {
            switch (method) {
                case "save": {
                    EmailVerificationToken token = (EmailVerificationToken) args[0];
                    if (token.getId() == null) {
                        token.setId(ids.getAndIncrement());
                    }
                    if (!store.contains(token)) {
                        store.add(token);
                    }
                    return token;
                }
                case "findByToken":
                    return store.stream().filter(t -> t.getToken().equals(args[0])).findFirst();
                case "findFirstByUserIdOrderByCreatedAtDesc":
                    return store.stream()
                            .filter(t -> t.getUser().getId().equals(args[0]))
                            .max(Comparator.comparing(EmailVerificationToken::getCreatedAt));
                default:
                    throw new UnsupportedOperationException("EmailVerificationTokenRepository." + method);
            }
        });
    }

    public static PasswordResetTokenRepository resetTokens(List<PasswordResetToken> store) {
        AtomicLong ids = new AtomicLong(1);
        return proxy(PasswordResetTokenRepository.class, (method, args) -> {
            switch (method) {
                case "save": {
                    PasswordResetToken token = (PasswordResetToken) args[0];
                    if (token.getId() == null) {
                        token.setId(ids.getAndIncrement());
                    }
                    if (!store.contains(token)) {
                        store.add(token);
                    }
                    return token;
                }
                case "findByTokenHash":
                    return store.stream().filter(t -> t.getTokenHash().equals(args[0])).findFirst();
                case "findFirstByUserIdOrderByCreatedAtDesc":
                    return store.stream()
                            .filter(t -> t.getUser().getId().equals(args[0]))
                            .max(Comparator.comparing(PasswordResetToken::getCreatedAt));
                case "findByUserIdAndUsedAtIsNull":
                    return new ArrayList<>(store.stream()
                            .filter(t -> t.getUser().getId().equals(args[0]) && t.getUsedAt() == null)
                            .toList());
                default:
                    throw new UnsupportedOperationException("PasswordResetTokenRepository." + method);
            }
        });
    }

    public static RefreshTokenRepository refreshTokens(List<RefreshToken> store) {
        AtomicLong ids = new AtomicLong(1);
        return proxy(RefreshTokenRepository.class, (method, args) -> {
            switch (method) {
                case "save": {
                    RefreshToken token = (RefreshToken) args[0];
                    if (token.getId() == null) {
                        token.setId(ids.getAndIncrement());
                    }
                    if (!store.contains(token)) {
                        store.add(token);
                    }
                    return token;
                }
                case "findByTokenHash":
                    return store.stream().filter(t -> t.getTokenHash().equals(args[0])).findFirst();
                case "revokeFamily": {
                    int changed = 0;
                    for (RefreshToken t : store) {
                        if (t.getFamilyId().equals(args[0]) && t.getRevokedAt() == null) {
                            t.setRevokedAt((LocalDateTime) args[1]);
                            changed++;
                        }
                    }
                    return changed;
                }
                case "revokeAllForUser": {
                    int changed = 0;
                    for (RefreshToken t : store) {
                        if (t.getUser().getId().equals(args[0]) && t.getRevokedAt() == null) {
                            t.setRevokedAt((LocalDateTime) args[1]);
                            changed++;
                        }
                    }
                    return changed;
                }
                case "deleteExpiredBefore": {
                    int before = store.size();
                    store.removeIf(t -> t.getUser().getId().equals(args[0]) && t.getExpiresAt().isBefore((LocalDateTime) args[1]));
                    return before - store.size();
                }
                default:
                    throw new UnsupportedOperationException("RefreshTokenRepository." + method);
            }
        });
    }

    /** Zalogowany użytkownik o podanym adresie (tak jak w tokenie JWT: nazwa = adres e-mail). */
    public static org.springframework.security.core.Authentication authentication(String name) {
        return proxy(org.springframework.security.core.Authentication.class, (method, args) -> {
            switch (method) {
                case "getName":
                    return name;
                case "isAuthenticated":
                    return Boolean.TRUE;
                default:
                    throw new UnsupportedOperationException("Authentication." + method);
            }
        });
    }

    /** Poczta, która zapamiętuje wysłane wiadomości albo (po {@link #failing}) rzuca wyjątek jak niedziałający serwer SMTP. */
    public static final class Mailbox {
        public final List<SimpleMailMessage> sent = new ArrayList<>();
        public boolean failing = false;

        public JavaMailSender sender() {
            return proxy(JavaMailSender.class, (method, args) -> {
                if (!method.equals("send")) {
                    throw new UnsupportedOperationException("JavaMailSender." + method);
                }
                if (failing) {
                    throw new IllegalStateException("SMTP niedostępny");
                }
                for (Object arg : args) {
                    if (arg instanceof SimpleMailMessage message) {
                        sent.add(message);
                    } else if (arg instanceof SimpleMailMessage[] messages) {
                        sent.addAll(List.of(messages));
                    }
                }
                return null;
            });
        }

        public SimpleMailMessage last() {
            return sent.get(sent.size() - 1);
        }
    }
}
