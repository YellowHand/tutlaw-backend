package pl.tutlaw.backend.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.settings.dto.OriginFilterRequest;
import pl.tutlaw.backend.settings.dto.OriginFilterResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filtr pochodzenia pytań w Testach: z których źródeł (pytania autorskie, pięć aplikacji) użytkownik chce
 * widzieć pytania. Zapisany na koncie, więc działa tak samo na każdym urządzeniu. Domyślnie wszystkie.
 * Zawsze jest wybrane co najmniej jedno pochodzenie.
 *
 * <p>Filtr dotyczy WYŁĄCZNIE pytań testowych: katalogu Testów, kolejek (nowe, zaległe, błędy), odznaki powtórek
 * i losowania egzaminu. Fiszki i statystyki go nie uwzględniają.
 */
@Service
@RequiredArgsConstructor
public class OriginFilterService {

    private final UserSettingsRepository repository;
    private final CardRepository cardRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    /** Aktywne pochodzenia użytkownika (nigdy puste). Używane przy budowie kolejek i licznika. */
    @Transactional(readOnly = true)
    public Set<QuestionOrigin> activeFor(Long userId) {
        return repository.findById(userId).map(s -> parse(s.getTestOrigins())).orElseGet(OriginFilterService::allOrigins);
    }

    @Transactional(readOnly = true)
    public OriginFilterResponse get(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return describe(activeFor(user.getId()));
    }

    @Transactional
    public OriginFilterResponse update(OriginFilterRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Set<QuestionOrigin> chosen = EnumSet.noneOf(QuestionOrigin.class);
        for (QuestionOrigin origin : request.origins()) {
            if (origin == null) {
                throw new IllegalArgumentException("Nieznane pochodzenie pytań.");
            }
            chosen.add(origin);
        }
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("Zaznacz co najmniej jedno pochodzenie pytań.");
        }

        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        settings.setTestOrigins(serialize(chosen));
        settings.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(settings);
        return describe(chosen);
    }

    private OriginFilterResponse describe(Set<QuestionOrigin> active) {
        Map<QuestionOrigin, Long> counts = new EnumMap<>(QuestionOrigin.class);
        for (CardRepository.OriginCount row : cardRepository.countTestQuestionsByOrigin()) {
            counts.put(row.getOrigin(), row.getTotal());
        }
        List<OriginFilterResponse.Option> options = new ArrayList<>();
        for (QuestionOrigin origin : QuestionOrigin.values()) {
            options.add(new OriginFilterResponse.Option(origin, counts.getOrDefault(origin, 0L)));
        }
        return new OriginFilterResponse(
                List.copyOf(EnumSet.copyOf(active)), active.size() == QuestionOrigin.values().length, options);
    }

    static Set<QuestionOrigin> allOrigins() {
        return EnumSet.allOf(QuestionOrigin.class);
    }

    /**
     * Zapis jako lista kodów oddzielona przecinkami w kolejności enumu. Wszystkie pochodzenia to {@code null}
     * (stan domyślny), żeby pochodzenia dodane w przyszłości były od razu widoczne dla tych, którzy niczego
     * nie zawężali.
     */
    static String serialize(Set<QuestionOrigin> origins) {
        if (origins.containsAll(allOrigins())) {
            return null;
        }
        return EnumSet.copyOf(origins).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /**
     * Odczyt zapisanej listy. Pusta wartość albo taka, w której nie ma ani jednego znanego kodu, daje wszystkie
     * pochodzenia (filtr nigdy nie może ukryć całego materiału). Nieznane kody są pomijane.
     */
    public static Set<QuestionOrigin> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return allOrigins();
        }
        Set<QuestionOrigin> result = EnumSet.noneOf(QuestionOrigin.class);
        for (String code : stored.split(",")) {
            try {
                result.add(QuestionOrigin.valueOf(code.trim()));
            } catch (IllegalArgumentException ignored) {
                // stary albo uszkodzony kod: pomijamy
            }
        }
        return result.isEmpty() ? allOrigins() : result;
    }
}
