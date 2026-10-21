package pl.gradusiuris.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.pool.PoolService;
import pl.gradusiuris.backend.review.dto.DifficultQuestionsResponse;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Zawieszanie pytań i lista trudnych pytań. Zawieszenie dotyczy jednego użytkownika i jednego trybu (testu albo
 * fiszki tego samego pytania) i niczego nie kasuje: stan (stabilność, termin, historia) zostaje, a po przywróceniu
 * pytanie wraca do kolejki zgodnie z terminem (zaległe pojawi się wśród najsłabiej zapamiętanych).
 */
@Service
@RequiredArgsConstructor
public class SuspensionService {

    /** Ile pozycji maksymalnie pokazuje każda z dwóch list. */
    static final int LIST_LIMIT = 200;

    private final UserQuestionStateRepository stateRepository;
    private final CurrentUserResolver currentUserResolver;
    private final PoolService poolService;
    private final LeechPolicy leechPolicy;
    private final Clock clock;

    /**
     * Zawiesza pytanie w trybie. Idempotentne: ponowne zawieszenie nic nie zmienia. Zawiesić można tylko pytanie,
     * które użytkownik już rozwiązywał w tym trybie (tylko takie ma stan); dla pozostałych odpowiadamy jak dla
     * nieistniejącego.
     */
    @Transactional
    public void suspend(Long questionId, ReviewMode mode, Authentication authentication) {
        UserQuestionState state = findState(questionId, mode, authentication);
        if (state.isSuspended()) {
            return;
        }
        state.setSuspended(true);
        state.setSuspendedAt(LocalDateTime.now(clock));
        stateRepository.save(state);
    }

    /** Przywraca zawieszone pytanie. Idempotentne. Termin powtórki zostaje taki, jaki był. */
    @Transactional
    public void unsuspend(Long questionId, ReviewMode mode, Authentication authentication) {
        UserQuestionState state = findState(questionId, mode, authentication);
        if (!state.isSuspended()) {
            return;
        }
        state.setSuspended(false);
        state.setSuspendedAt(null);
        stateRepository.save(state);
    }

    @Transactional(readOnly = true)
    public DifficultQuestionsResponse list(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        PageRequest firstPage = PageRequest.of(0, LIST_LIMIT);

        List<DifficultQuestionsResponse.Item> suspended = stateRepository.findSuspendedRows(user.getId(), firstPage).stream()
                .map(SuspensionService::toItem)
                .toList();
        List<DifficultQuestionsResponse.Item> leeches = stateRepository
                .findLeechRows(user.getId(), leechPolicy.threshold(), poolService.forUser(user.getId()).actIds(), firstPage)
                .stream()
                .map(SuspensionService::toItem)
                .toList();

        return new DifficultQuestionsResponse(leechPolicy.threshold(), suspended, leeches);
    }

    private UserQuestionState findState(Long questionId, ReviewMode mode, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return stateRepository.findByUserIdAndQuestionIdAndMode(user.getId(), questionId, mode)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
    }

    private static DifficultQuestionsResponse.Item toItem(UserQuestionStateRepository.DifficultRow row) {
        return new DifficultQuestionsResponse.Item(
                row.getQuestionId(), row.getMode(), row.getBody(), row.getActName(),
                row.getLapses() == null ? 0 : row.getLapses(), row.getSuspendedAt());
    }
}
