package pl.tutlaw.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Maile po zatwierdzeniu transakcji: kiedy wychodzą, a kiedy nie, i że błąd poczty nigdy nie psuje operacji. */
class AfterCommitMailerTest {

    @Test
    void outsideATransactionTheMailIsSubmittedImmediately() {
        AfterCommitMailer mailer = new AfterCommitMailer(Runnable::run);
        List<String> sent = new ArrayList<>();

        mailer.send("test", () -> sent.add("mail"));

        assertThat(sent).containsExactly("mail");
    }

    @Test
    void insideATransactionTheMailWaitsForTheCommit() {
        AfterCommitMailer mailer = new AfterCommitMailer(Runnable::run);
        List<String> sent = new ArrayList<>();

        TransactionSynchronizationManager.initSynchronization();
        try {
            mailer.send("test", () -> sent.add("mail"));
            assertThat(sent).isEmpty();

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            assertThat(sent).containsExactly("mail");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void aRolledBackTransactionSendsNothing() {
        AfterCommitMailer mailer = new AfterCommitMailer(Runnable::run);
        List<String> sent = new ArrayList<>();

        TransactionSynchronizationManager.initSynchronization();
        try {
            mailer.send("test", () -> sent.add("mail"));
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(sent).isEmpty();
    }

    @Test
    void aFailingMailIsSwallowed() {
        AfterCommitMailer mailer = new AfterCommitMailer(Runnable::run);

        assertThatCode(() -> mailer.send("test", () -> {
            throw new IllegalStateException("SMTP niedostępny");
        })).doesNotThrowAnyException();
    }

    @Test
    void aFullOrClosedQueueDropsTheMailInsteadOfBreakingTheOperation() {
        AfterCommitMailer mailer = new AfterCommitMailer(task -> {
            throw new RejectedExecutionException("pełna");
        });

        assertThatCode(() -> mailer.send("test", () -> {
        })).doesNotThrowAnyException();
    }

    @Test
    void theDefaultMailerSendsOnItsOwnThreadAndShutsDownCleanly() throws Exception {
        AfterCommitMailer mailer = new AfterCommitMailer();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        mailer.send("test", () -> {
            threadName.set(Thread.currentThread().getName());
            done.countDown();
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).startsWith("mail-sender-").isNotEqualTo(Thread.currentThread().getName());
        mailer.destroy();
        // Po zamknięciu kolejne zlecenie jest odrzucone, a nie wykonane, i nie rzuca wyjątku.
        assertThatCode(() -> mailer.send("test", () -> {
        })).doesNotThrowAnyException();
    }
}
