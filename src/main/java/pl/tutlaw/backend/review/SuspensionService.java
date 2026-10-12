package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.review.dto.DifficultCardsResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Zawieszanie pytań i lista trudnych pytań. Zawieszenie dotyczy tylko jednego użytkownika i niczego nie kasuje:
 * stan karty (stabilność, termin, historia) zostaje, a po przywróceniu karta wraca do kolejki zgodnie z terminem
 * (zaległa pojawi się wśród najsłabiej zapamiętanych).
 */
@Service
@RequiredArgsConstructor
public class SuspensionService {

    /** Ile pozycji maksymalnie pokazuje każda z dwóch list. */
    static final int LIST_LIMIT = 200;

    private final UserCardStateRepository stateRepository;
    private final CurrentUserResolver currentUserResolver;
    private final LeechPolicy leechPolicy;
    private final Clock clock;

    /**
     * Zawiesza pytanie. Idempotentne: ponowne zawieszenie nic nie zmienia. Zawiesić można tylko pytanie,
     * które użytkownik już rozwiązywał (tylko takie ma stan); dla pozostałych odpowiadamy jak dla nieistniejącego.
     */
    @Transactional
    public void suspend(Long cardId, Authentication authentication) {
        UserCardState state = findState(cardId, authentication);
        if (state.isSuspended()) {
            return;
        }
        state.setSuspended(true);
        state.setSuspendedAt(LocalDateTime.now(clock));
        stateRepository.save(state);
    }

    /** Przywraca zawieszone pytanie. Idempotentne. Termin powtórki zostaje taki, jaki był. */
    @Transactional
    public void unsuspend(Long cardId, Authentication authentication) {
        UserCardState state = findState(cardId, authentication);
        if (!state.isSuspended()) {
            return;
        }
        state.setSuspended(false);
        state.setSuspendedAt(null);
        stateRepository.save(state);
    }

    @Transactional(readOnly = true)
    public DifficultCardsResponse list(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        PageRequest firstPage = PageRequest.of(0, LIST_LIMIT);

        List<DifficultCardsResponse.Item> suspended = stateRepository.findSuspendedRows(user.getId(), firstPage).stream()
                .map(SuspensionService::toItem)
                .toList();
        List<DifficultCardsResponse.Item> leeches = stateRepository
                .findLeechRows(user.getId(), leechPolicy.threshold(), firstPage).stream()
                .map(SuspensionService::toItem)
                .toList();

        return new DifficultCardsResponse(leechPolicy.threshold(), suspended, leeches);
    }

    private UserCardState findState(Long cardId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return stateRepository.findByUserIdAndCardId(user.getId(), cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found"));
    }

    private static DifficultCardsResponse.Item toItem(UserCardStateRepository.DifficultRow row) {
        return new DifficultCardsResponse.Item(
                row.getCardId(), row.getFront(), row.getSourceName(),
                row.getLapses() == null ? 0 : row.getLapses(), row.getSuspendedAt());
    }
}
