/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_resolver.maven_resolver_named_locks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.aether.named.NamedLock;
import org.eclipse.aether.named.NamedLockKey;
import org.eclipse.aether.named.providers.FileLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalReadWriteLockNamedLockFactory;
import org.eclipse.aether.named.providers.LocalSemaphoreNamedLockFactory;
import org.eclipse.aether.named.providers.NoopNamedLockFactory;
import org.eclipse.aether.named.support.LockUpgradeNotSupportedException;
import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.eclipse.aether.named.support.NamedLockSupport;
import org.eclipse.aether.named.support.ReadWriteLockNamedLock;
import org.eclipse.aether.named.support.Retry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Maven_resolver_named_locksTest {
    private static final long LOCK_TIMEOUT_MILLIS = 25L;

    @Test
    void providerNamesIdentifyAvailableImplementations() {
        assertThat(NoopNamedLockFactory.NAME).isEqualTo("noop");
        assertThat(LocalReadWriteLockNamedLockFactory.NAME).isEqualTo("rwlock-local");
        assertThat(LocalSemaphoreNamedLockFactory.NAME).isEqualTo("semaphore-local");
        assertThat(FileLockNamedLockFactory.NAME).isEqualTo("file-lock");
    }

    @Test
    void factoryReusesNamedLockUntilEveryHandleIsClosed() {
        LocalReadWriteLockNamedLockFactory factory = new LocalReadWriteLockNamedLockFactory();
        NamedLockSupport first = namedLockSupport(factory, "repository");
        NamedLockSupport second = namedLockSupport(factory, "repository");
        NamedLockSupport differentName = namedLockSupport(factory, "metadata");

        assertThat(first).isSameAs(second);
        assertThat(first).isNotSameAs(differentName);
        assertThat(first.key().name()).isEqualTo("repository");
        assertThat(first.key().resources()).isEmpty();
        assertThat(first).hasToString("ReadWriteLockNamedLock{key='NamedLockKey{name='repository', resources=[]}'}");
        assertThat(first.diagnosticState()).isEmpty();

        first.close();
        second.close();

        NamedLockSupport replacement = namedLockSupport(factory, "repository");
        assertThat(replacement).isNotSameAs(first);

        replacement.close();
        differentName.close();
        factory.closeLock(namedLockKey("already-closed"));
        factory.shutdown();
    }

    @Test
    void noopProviderAcceptsEverySharedAndExclusiveRequest() throws Exception {
        NoopNamedLockFactory factory = new NoopNamedLockFactory();
        NamedLock lock = namedLock(factory, "anything");
        NamedLock competingHandle = namedLock(factory, "anything");

        try {
            assertThat(lock.lockShared(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(lockSharedInWorker(competingHandle)).isTrue();
            assertThat(lockExclusivelyInWorker(competingHandle)).isTrue();

            assertThat(lock.lockExclusively(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(lockSharedInWorker(competingHandle)).isTrue();
            assertThat(lockExclusivelyInWorker(competingHandle)).isTrue();

            lock.unlock();
            lock.unlock();
            lock.unlock();
        } finally {
            lock.close();
            competingHandle.close();
        }
    }

    @Test
    void localReadWriteLockCoordinatesReadersAndWriters() throws Exception {
        LocalReadWriteLockNamedLockFactory factory = new LocalReadWriteLockNamedLockFactory();

        assertStatefulLockCoordinatesReadersAndWriters(factory, "artifact");
    }

    @Test
    void localSemaphoreLockCoordinatesReadersAndWriters() throws Exception {
        LocalSemaphoreNamedLockFactory factory = new LocalSemaphoreNamedLockFactory();

        assertStatefulLockCoordinatesReadersAndWriters(factory, "artifact");
    }

    @Test
    void fileLockCoordinatesReadersAndWriters(@TempDir Path tempDir) throws Exception {
        FileLockNamedLockFactory factory = new FileLockNamedLockFactory();
        String lockName = tempDir.resolve("locks").resolve("repository.lck").toUri().toString();

        assertStatefulLockCoordinatesReadersAndWriters(factory, lockName);
    }

    @Test
    void statefulLocksRejectSharedToExclusiveUpgrade(@TempDir Path tempDir) throws Exception {
        assertSharedToExclusiveUpgradeIsRejected(new LocalReadWriteLockNamedLockFactory(), "rw-upgrade");
        assertSharedToExclusiveUpgradeIsRejected(new LocalSemaphoreNamedLockFactory(), "semaphore-upgrade");
        assertSharedToExclusiveUpgradeIsRejected(
                new FileLockNamedLockFactory(), tempDir.resolve("locks").resolve("upgrade.lck").toUri().toString());
    }

    @Test
    void statefulLocksAllowExclusiveReentryAndSharedDowngrade(@TempDir Path tempDir) throws Exception {
        assertExclusiveReentryAndSharedDowngrade(new LocalReadWriteLockNamedLockFactory(), "rw-downgrade");
        assertExclusiveReentryAndSharedDowngrade(new LocalSemaphoreNamedLockFactory(), "semaphore-downgrade");
        assertExclusiveReentryAndSharedDowngrade(
                new FileLockNamedLockFactory(), tempDir.resolve("locks").resolve("downgrade.lck").toUri().toString());
    }

    @Test
    void statefulLocksRejectUnlockWithoutHeldLock(@TempDir Path tempDir) {
        assertUnlockWithoutHeldLockFails(new LocalReadWriteLockNamedLockFactory(), "rw-unlocked");
        assertUnlockWithoutHeldLockFails(new LocalSemaphoreNamedLockFactory(), "semaphore-unlocked");
        assertUnlockWithoutHeldLockFails(
                new FileLockNamedLockFactory(), tempDir.resolve("locks").resolve("unlocked.lck").toUri().toString());
    }

    @Test
    void diagnosticFactoryTracksCurrentThreadLockStateAndReturnsFailures() throws Exception {
        DiagnosticReadWriteLockFactory factory = new DiagnosticReadWriteLockFactory();
        NamedLockSupport lock = namedLockSupport(factory, "diagnostic");
        RuntimeException failure = new RuntimeException("boom");

        assertThat(factory.isDiagnosticEnabled()).isTrue();
        assertThat(lock.diagnosticState()).isEmpty();

        assertThat(lock.lockShared(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        try {
            Deque<String> currentThreadState = lock.diagnosticState().get(Thread.currentThread());
            assertThat(currentThreadState).containsExactly("shared");
            assertThat(factory.onFailure(failure)).isSameAs(failure);
        } finally {
            lock.unlock();
            lock.close();
        }

        assertThat(lock.diagnosticState().get(Thread.currentThread())).isEmpty();
    }

    @Test
    void retryReturnsFirstNonNullResult() throws Exception {
        AtomicInteger attempts = new AtomicInteger();

        String result = Retry.retry(
                1L,
                TimeUnit.SECONDS,
                0L,
                () -> attempts.incrementAndGet() == 3 ? "resolved" : null,
                exception -> true,
                "fallback");

        assertThat(result).isEqualTo("resolved");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void retryReturnsFallbackAfterRetryableFailuresAndStopsOnNonRetryableFailures() throws Exception {
        AtomicInteger retryableAttempts = new AtomicInteger();

        String fallback = Retry.retry(
                3,
                0L,
                () -> {
                    retryableAttempts.incrementAndGet();
                    throw new IllegalArgumentException("temporary");
                },
                IllegalArgumentException.class::isInstance,
                "fallback");

        assertThat(fallback).isEqualTo("fallback");
        assertThat(retryableAttempts).hasValue(3);

        NonRetryableException failure = new NonRetryableException("fatal");
        assertThatThrownBy(() -> Retry.retry(3, 0L, throwing(failure), exception -> true, "fallback"))
                .isSameAs(failure);
    }

    @Test
    void retryStopsImmediatelyWhenFailurePredicateRejectsException() {
        AtomicInteger attempts = new AtomicInteger();
        IllegalArgumentException failure = new IllegalArgumentException("permanent");

        assertThatThrownBy(() -> Retry.retry(
                        5,
                        0L,
                        () -> {
                            attempts.incrementAndGet();
                            throw failure;
                        },
                        exception -> false,
                        "fallback"))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(failure);
        assertThat(attempts).hasValue(1);
    }

    private static void assertStatefulLockCoordinatesReadersAndWriters(NamedLockFactorySupport factory, String lockName)
            throws Exception {
        NamedLock owner = namedLock(factory, lockName);
        NamedLock competitor = namedLock(factory, lockName);

        try {
            assertThat(owner.lockShared(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            try {
                assertThat(lockSharedInWorker(competitor)).isTrue();
                assertThat(lockExclusivelyInWorker(competitor)).isFalse();
            } finally {
                owner.unlock();
            }

            assertThat(owner.lockExclusively(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            try {
                assertThat(lockSharedInWorker(competitor)).isFalse();
                assertThat(lockExclusivelyInWorker(competitor)).isFalse();
            } finally {
                owner.unlock();
            }

            assertThat(lockExclusivelyInWorker(competitor)).isTrue();
        } finally {
            owner.close();
            competitor.close();
        }
    }

    private static void assertSharedToExclusiveUpgradeIsRejected(NamedLockFactorySupport factory, String lockName)
            throws Exception {
        NamedLock lock = namedLock(factory, lockName);

        try {
            assertThat(lock.lockShared(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            assertThatThrownBy(() -> lock.lockExclusively(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    .isInstanceOf(LockUpgradeNotSupportedException.class)
                    .hasMessageContaining(lockName);
        } finally {
            lock.unlock();
            lock.close();
        }
    }

    private static void assertExclusiveReentryAndSharedDowngrade(NamedLockFactorySupport factory, String lockName)
            throws Exception {
        NamedLock owner = namedLock(factory, lockName);
        NamedLock competitor = namedLock(factory, lockName);
        int ownerHeldLocks = 0;

        try {
            assertThat(owner.lockExclusively(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            ownerHeldLocks++;
            assertThat(owner.lockExclusively(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            ownerHeldLocks++;
            assertThat(owner.lockShared(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
            ownerHeldLocks++;

            assertThat(lockSharedInWorker(competitor)).isFalse();
            assertThat(lockExclusivelyInWorker(competitor)).isFalse();

            owner.unlock();
            ownerHeldLocks--;
            assertThat(lockSharedInWorker(competitor)).isFalse();

            owner.unlock();
            ownerHeldLocks--;
            assertThat(lockExclusivelyInWorker(competitor)).isFalse();
        } finally {
            while (ownerHeldLocks > 0) {
                owner.unlock();
                ownerHeldLocks--;
            }
            owner.close();
            competitor.close();
        }
    }

    private static void assertUnlockWithoutHeldLockFails(NamedLockFactorySupport factory, String lockName) {
        NamedLock lock = namedLock(factory, lockName);

        try {
            assertThatIllegalStateException()
                    .isThrownBy(lock::unlock)
                    .withMessage("Wrong API usage: unlock without lock");
        } finally {
            lock.close();
        }
    }

    private static NamedLock namedLock(NamedLockFactorySupport factory, String lockName) {
        return factory.getLock(namedLockKey(lockName));
    }

    private static NamedLockSupport namedLockSupport(NamedLockFactorySupport factory, String lockName) {
        return (NamedLockSupport) namedLock(factory, lockName);
    }

    private static NamedLockKey namedLockKey(String lockName) {
        return NamedLockKey.of(lockName);
    }

    private static boolean lockSharedInWorker(NamedLock lock) throws Exception {
        return runInWorker(() -> {
            boolean locked = lock.lockShared(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (locked) {
                lock.unlock();
            }
            return locked;
        });
    }

    private static boolean lockExclusivelyInWorker(NamedLock lock) throws Exception {
        return runInWorker(() -> {
            boolean locked = lock.lockExclusively(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (locked) {
                lock.unlock();
            }
            return locked;
        });
    }

    private static boolean runInWorker(Callable<Boolean> callable) throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executorService.submit(callable);
        try {
            return future.get(5L, TimeUnit.SECONDS);
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(5L, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Callable<String> throwing(RuntimeException exception) {
        return () -> {
            throw exception;
        };
    }

    private static final class DiagnosticReadWriteLockFactory extends NamedLockFactorySupport {
        private DiagnosticReadWriteLockFactory() {
            super(true);
        }

        @Override
        protected NamedLockSupport createLock(NamedLockKey key) {
            return new ReadWriteLockNamedLock(key, this, new ReentrantReadWriteLock());
        }
    }

    private static final class NonRetryableException extends RuntimeException implements Retry.DoNotRetry {
        private NonRetryableException(String message) {
            super(message);
        }
    }
}
