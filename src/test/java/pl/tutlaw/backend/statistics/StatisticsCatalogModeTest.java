package pl.tutlaw.backend.statistics;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.chapter.ChapterRepository;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.FsrsWeights;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.source.SourceType;
import pl.tutlaw.backend.statistics.dto.ChapterMasteryResponse;
import pl.tutlaw.backend.statistics.dto.SourceProgressResponse;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Postęp w źródłach i opanowanie rozdziałów w trybach Fiszki / Testy / razem. Sprawdza, że tryb zamienia się na właściwe
 * flagi w zapytaniach (a ich treść pilnuje README i uruchomienie na prawdziwej bazie), oraz że bez trybu liczy się wszystko.
 */
class StatisticsCatalogModeTest {

    private static GroupCount group(long id, long total) {
        return new GroupCount() {
            @Override
            public Long getGroupId() {
                return id;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private static final class Fixture {
        /** Wywołania zapytań o liczby: "nazwa:flagaFiszek/flagaTestów". */
        final List<String> calls = new ArrayList<>();
        final StatisticsService service;
        final org.springframework.security.core.Authentication jan = FakeRepos.authentication("jan@gmail.com");

        Fixture() {
            User user = new User();
            user.setId(1L);
            user.setPrivateEmail("jan@gmail.com");

            CardRepository cards = FakeRepos.proxy(CardRepository.class, (method, args) -> {
                switch (method) {
                    case "countBySourceForSubject":
                        calls.add("totalBySource:" + args[2] + "/" + args[3]);
                        return List.of(group(100L, 10L));
                    case "countByChapterForSource":
                        calls.add("totalByChapter:" + args[2] + "/" + args[3]);
                        return List.of(group(1000L, 4L));
                    default:
                        throw new UnsupportedOperationException("CardRepository." + method);
                }
            });
            UserCardStateRepository states = FakeRepos.proxy(UserCardStateRepository.class, (method, args) -> {
                switch (method) {
                    case "countStartedBySource":
                        calls.add("startedBySource:" + args[2] + "/" + args[3]);
                        return List.of(group(100L, 5L));
                    case "countStartedByChapter":
                        calls.add("startedByChapter:" + args[3] + "/" + args[4]);
                        return List.of(group(1000L, 2L));
                    default:
                        throw new UnsupportedOperationException("UserCardStateRepository." + method);
                }
            });
            SourceRepository sources = FakeRepos.proxy(SourceRepository.class, (method, args) -> {
                if (method.equals("findBySubjectId")) {
                    Source source = new Source();
                    source.setId(100L);
                    source.setName("Podręcznik");
                    source.setSourceType(SourceType.TEXTBOOK);
                    return List.of(source);
                }
                throw new UnsupportedOperationException("SourceRepository." + method);
            });
            ChapterRepository chapters = FakeRepos.proxy(ChapterRepository.class, (method, args) -> {
                if (method.equals("findBySourceIdOrderByOrderIndexAsc")) {
                    Chapter chapter = new Chapter();
                    chapter.setId(1000L);
                    chapter.setTitle("Rozdział I");
                    return List.of(chapter);
                }
                throw new UnsupportedOperationException("ChapterRepository." + method);
            });
            List<User> users = new ArrayList<>(List.of(user));
            service = new StatisticsService(
                    cards, chapters, sources, states, FakeRepos.proxy(ReviewLogRepository.class, (m, a) -> {
                        throw new UnsupportedOperationException(m);
                    }),
                    new CurrentUserResolver(FakeRepos.users(users)),
                    new FsrsEngine(FsrsWeights.defaults(), 0.9), MutableClock.at("2026-10-12T08:00:00Z"));
        }

        String distinct() {
            return calls.stream().map(c -> c.substring(c.indexOf(':') + 1)).distinct().reduce((a, b) -> a + "," + b).orElse("");
        }
    }

    @Test
    void sourceProgress_flashcardMode_asksForFlashcardsOnly() {
        Fixture f = new Fixture();

        List<SourceProgressResponse> progress = f.service.getSourceProgress(7L, ReviewMode.FLASHCARD, f.jan);

        assertThat(f.calls).containsExactlyInAnyOrder("totalBySource:true/false", "startedBySource:true/false");
        assertThat(progress).hasSize(1);
        assertThat(progress.get(0).totalCards()).isEqualTo(10);
        assertThat(progress.get(0).cardsStarted()).isEqualTo(5);
    }

    @Test
    void sourceProgress_testMode_asksForTestQuestionsOnly() {
        Fixture f = new Fixture();

        f.service.getSourceProgress(7L, ReviewMode.TEST, f.jan);

        assertThat(f.distinct()).isEqualTo("false/true");
    }

    @Test
    void sourceProgress_withoutMode_countsEverything() {
        Fixture f = new Fixture();

        f.service.getSourceProgress(7L, null, f.jan);

        assertThat(f.distinct()).isEqualTo("true/true");
    }

    @Test
    void chapterMastery_modeReachesBothTotalsAndStartedCounts() {
        Fixture f = new Fixture();

        List<ChapterMasteryResponse> mastery = f.service.getMasteryBySource(3L, ReviewMode.FLASHCARD, f.jan);

        // jedno zapytanie o sumy + dwa o rozpoczęte (wszystkie i opanowane), każde z tymi samymi flagami
        assertThat(f.calls).hasSize(3);
        assertThat(f.distinct()).isEqualTo("true/false");
        assertThat(mastery).hasSize(1);
        assertThat(mastery.get(0).totalCards()).isEqualTo(4);
    }

    @Test
    void chapterMastery_withoutMode_countsEverything() {
        Fixture f = new Fixture();

        f.service.getMasteryBySource(3L, null, f.jan);

        assertThat(f.distinct()).isEqualTo("true/true");
    }
}
