package pl.tutlaw.backend.question;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Uruchamia {@link LawChangeService}: raz przy starcie aplikacji (po wdrożeniu nowych pytań ze zmianami) i codziennie
 * tuż po północy (czasu aplikacji), kiedy zaczynają obowiązywać zmiany z datą „na dziś". Błąd nie przerywa startu
 * aplikacji ani kolejnych przebiegów: pytania wrócą przy następnym.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LawChangeJob implements ApplicationRunner {

    private final LawChangeService service;

    @Override
    public void run(ApplicationArguments args) {
        runSafely();
    }

    @Scheduled(cron = "0 10 0 * * *")
    public void daily() {
        runSafely();
    }

    private void runSafely() {
        try {
            service.requeueChangedQuestions();
        } catch (RuntimeException e) {
            log.error("Law changes: requeue failed, will retry at the next run", e);
        }
    }
}
