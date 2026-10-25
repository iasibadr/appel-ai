package live.pharmaradio.reponsevoice.autonome;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkCleanupTest {
    @Test
    public void connectionCleanupNeverRunsOnTheCallerThread() throws Exception {
        Thread caller = Thread.currentThread();
        AtomicReference<Thread> worker = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        NetworkCleanup.start(() -> {
            worker.set(Thread.currentThread());
            finished.countDown();
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertNotSame(caller, worker.get());
        assertEquals("voice-network-cleanup", worker.get().getName());
    }

    @Test
    public void blockedCleanupDoesNotBlockTheCallerOrAnotherCleanup() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            NetworkCleanup.start(() -> {
                entered.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            NetworkCleanup.start(finished::countDown);
            assertTrue(finished.await(5, TimeUnit.SECONDS));
        } finally { release.countDown(); }
    }
}
