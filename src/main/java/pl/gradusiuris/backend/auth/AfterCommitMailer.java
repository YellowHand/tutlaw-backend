package pl.gradusiuris.backend.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wysyła maile dopiero po zatwierdzeniu transakcji i poza wątkiem żądania.
 * <p>
 * Po co:
 * <ul>
 *   <li>mail nie wychodzi, jeśli transakcja się nie powiodła (inaczej użytkownik dostałby link do konta albo tokenu,
 *       których w bazie nie ma),</li>
 *   <li>wolny albo niedostępny serwer SMTP nie wydłuża odpowiedzi i nie zdradza różnicą czasu, że konto istnieje
 *       (rejestracja, "zapomniałem hasła" i "wyślij link ponownie" kończą się tak samo szybko dla każdego adresu),</li>
 *   <li>błąd poczty jest tylko logowany i nigdy nie psuje operacji, która już się udała.</li>
 * </ul>
 * Kolejka jest ograniczona: gdy poczta stoi, nadmiar maili jest odrzucany z wpisem w logu, zamiast zapychać pamięć.
 */
@Component
@Slf4j
public class AfterCommitMailer implements DisposableBean {

    private static final int THREADS = 2;
    private static final int QUEUE_SIZE = 200;
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private final Executor executor;
    /** Własna pula (null, gdy executor podano z zewnątrz, np. w testach). Zamykana razem z aplikacją. */
    private final ThreadPoolExecutor ownPool;

    @Autowired
    public AfterCommitMailer() {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                THREADS, THREADS, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(QUEUE_SIZE),
                runnable -> {
                    Thread thread = new Thread(runnable, "mail-sender-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        this.ownPool = pool;
        this.executor = pool;
    }

    /** Do testów: własny executor (np. {@code Runnable::run}, żeby wysyłka była synchroniczna). */
    public AfterCommitMailer(Executor executor) {
        this.ownPool = null;
        this.executor = executor;
    }

    /**
     * Zleca wysyłkę. W transakcji: po jej zatwierdzeniu (przy wycofaniu nic nie wychodzi). Bez transakcji: od razu.
     * {@code description} trafia do logu przy błędzie, więc bez adresów e-mail (np. "aktywacja konta 12").
     */
    public void send(String description, Runnable mailTask) {
        Runnable guarded = () -> {
            try {
                mailTask.run();
            } catch (RuntimeException e) {
                log.error("Nie udało się wysłać maila ({})", description, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(guarded, description);
                }
            });
        } else {
            submit(guarded, description);
        }
    }

    private void submit(Runnable task, String description) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            log.error("Kolejka wysyłki maili jest pełna albo zamknięta, mail pominięty ({})", description);
        }
    }

    @Override
    public void destroy() throws InterruptedException {
        if (ownPool == null) {
            return;
        }
        ownPool.shutdown();
        if (!ownPool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
            ownPool.shutdownNow();
        }
    }
}
