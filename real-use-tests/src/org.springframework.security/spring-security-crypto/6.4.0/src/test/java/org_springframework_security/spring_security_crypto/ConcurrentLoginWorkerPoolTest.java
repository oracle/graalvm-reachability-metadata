/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.RsaAlgorithm;
import org.springframework.security.crypto.encrypt.RsaSecretEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

/**
 * Concurrent use of singleton `PasswordEncoder` and `TextEncryptor` beans from a pool of
 * request worker threads, the way a servlet container calls the beans a Spring context wires once.
 *
 * A login service shares one `DelegatingPasswordEncoder` and one session-token encryptor across
 * eight workers. Registrations and logins race on the same encoder, every legacy hash is
 * re-hashed exactly once even when several logins for the same account arrive together, and
 * tokens sealed on one worker open on another.
 */
public class ConcurrentLoginWorkerPoolTest {

    private static final int WORKERS = 8;

    private ExecutorService pool;

    @BeforeEach
    void startPool() {
        pool = Executors.newFixedThreadPool(WORKERS);
    }

    @AfterEach
    void stopPool() throws InterruptedException {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "workers must stop");
    }

    @Test
    void registersAndVerifiesAccountsFromEveryWorker() throws Exception {
        LoginService service = new LoginService(passwordEncoderBean());
        int accounts = 48;

        List<Callable<String>> registrations = new ArrayList<>();
        for (int i = 0; i < accounts; i++) {
            String user = "user-" + i;
            registrations.add(() -> {
                service.register(user, "pass-" + user);
                return service.storedHash(user);
            });
        }
        List<String> hashes = runTogether(registrations);

        assertEquals(accounts, service.size());
        assertEquals(accounts, new HashSet<>(hashes).size(), "each racing encode draws its own salt");
        for (String hash : hashes) {
            assertTrue(hash.startsWith("{bcrypt}$2a$04$"), hash);
            assertEquals("{bcrypt}".length() + 60, hash.length());
        }

        List<Callable<String>> logins = new ArrayList<>();
        for (int i = 0; i < accounts; i++) {
            String user = "user-" + i;
            logins.add(() -> service.login(user, "pass-" + user) + "/" + service.login(user, "pass-" + user + "!"));
        }
        for (String outcome : runTogether(logins)) {
            assertEquals("true/false", outcome);
        }
        assertEquals(0, service.rehashCount(), "current bcrypt hashes are never rewritten");
    }

    @Test
    void upgradesEachLegacyHashExactlyOnceUnderConcurrentLogins() throws Exception {
        Pbkdf2PasswordEncoder pbkdf2 = new Pbkdf2PasswordEncoder("", 16, 1000,
                SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        SCryptPasswordEncoder scrypt = new SCryptPasswordEncoder(1024, 8, 1, 32, 16);
        LoginService service = new LoginService(passwordEncoderBean());
        Map<String, String> passwords = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            passwords.put("pbkdf2-" + i, "harbor-" + i);
            service.importRow("pbkdf2-" + i, "{pbkdf2}" + pbkdf2.encode("harbor-" + i));
            passwords.put("scrypt-" + i, "meadow-" + i);
            service.importRow("scrypt-" + i, "{scrypt}" + scrypt.encode("meadow-" + i));
        }

        List<Callable<Boolean>> logins = new ArrayList<>();
        for (int attempt = 0; attempt < 4; attempt++) {
            for (Map.Entry<String, String> account : passwords.entrySet()) {
                logins.add(() -> service.login(account.getKey(), account.getValue()));
            }
        }
        List<Boolean> outcomes = runTogether(logins);

        assertEquals(passwords.size() * 4, outcomes.size());
        assertFalse(outcomes.contains(Boolean.FALSE), "a login racing an upgrade still succeeds");
        assertEquals(passwords.size(), service.rehashCount(), "one rehash per legacy account");
        for (Map.Entry<String, String> account : passwords.entrySet()) {
            String stored = service.storedHash(account.getKey());
            assertTrue(stored.startsWith("{bcrypt}$2a$04$"), stored);
            assertTrue(service.login(account.getKey(), account.getValue()));
        }
        assertEquals(passwords.size(), service.rehashCount());
    }

    @Test
    void opensSessionTokensOnADifferentWorkerThanSealedThem() throws Exception {
        String salt = KeyGenerators.string().generateKey();
        SessionTokens symmetric = new SessionTokens(Encryptors.delux("session-signing-secret", salt));
        SessionTokens asymmetric = new SessionTokens(new RsaSecretEncryptor(RsaAlgorithm.OAEP, salt, true));
        int sessions = 40;

        for (SessionTokens tokens : List.of(symmetric, asymmetric)) {
            List<Callable<String>> seals = new ArrayList<>();
            for (int i = 0; i < sessions; i++) {
                String claims = "sub=user-" + i + ";roles=reader,writer;tenant=t" + (i % 3);
                seals.add(() -> tokens.seal(claims));
            }
            List<String> sealed = runTogether(seals);
            assertEquals(sessions, new HashSet<>(sealed).size(), "every token carries a fresh IV");

            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < sessions; i++) {
                order.add(i);
            }
            Collections.reverse(order);
            List<Callable<String>> opens = new ArrayList<>();
            for (int index : order) {
                opens.add(() -> index + ":" + tokens.open(sealed.get(index)));
            }
            for (String opened : runTogether(opens)) {
                int index = Integer.parseInt(opened.substring(0, opened.indexOf(':')));
                assertEquals(index + ":sub=user-" + index + ";roles=reader,writer;tenant=t" + (index % 3), opened);
            }
            assertTrue(tokens.workerCount() > 1, "work was spread across the pool");
        }
    }

    /// Builds the application's `PasswordEncoder` bean with cheap work factors for the test run.
    private static PasswordEncoder passwordEncoderBean() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(4));
        encoders.put("pbkdf2", new Pbkdf2PasswordEncoder("", 16, 1000, SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256));
        encoders.put("scrypt", new SCryptPasswordEncoder(1024, 8, 1, 32, 16));
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    /// Releases every task at once behind a start gate and returns the results in task order.
    /// The gate opens only once every worker holds a task, so a reused pool cannot let one
    /// worker drain the queue alone.
    private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(Math.min(WORKERS, tasks.size()));
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(gate.await(30, TimeUnit.SECONDS), "start gate");
                return task.call();
            }));
        }
        assertTrue(ready.await(30, TimeUnit.SECONDS), "every worker holds a task");
        gate.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(120, TimeUnit.SECONDS));
        }
        return results;
    }

    /// Account store that verifies logins and re-hashes outdated credentials atomically per user.
    private static final class LoginService {
        private final PasswordEncoder encoder;
        private final Map<String, String> users = new ConcurrentHashMap<>();
        private final AtomicInteger rehashes = new AtomicInteger();

        LoginService(PasswordEncoder encoder) {
            this.encoder = encoder;
        }

        void register(String user, String password) {
            users.put(user, encoder.encode(password));
        }

        void importRow(String user, String storedHash) {
            users.put(user, storedHash);
        }

        boolean login(String user, String password) {
            String stored = users.get(user);
            if (stored == null || !encoder.matches(password, stored)) {
                return false;
            }
            if (encoder.upgradeEncoding(stored)) {
                users.compute(user, (name, current) -> {
                    if (!encoder.upgradeEncoding(current)) {
                        return current;
                    }
                    rehashes.incrementAndGet();
                    return encoder.encode(password);
                });
            }
            return true;
        }

        String storedHash(String user) {
            return users.get(user);
        }

        int size() {
            return users.size();
        }

        int rehashCount() {
            return rehashes.get();
        }
    }

    /// Seals session claims into opaque tokens with one shared encryptor and records which
    /// workers touched it.
    private static final class SessionTokens {
        private final TextEncryptor encryptor;
        private final Set<String> workers = ConcurrentHashMap.newKeySet();

        SessionTokens(TextEncryptor encryptor) {
            this.encryptor = encryptor;
        }

        String seal(String claims) {
            workers.add(Thread.currentThread().getName());
            return encryptor.encrypt(claims);
        }

        String open(String token) {
            workers.add(Thread.currentThread().getName());
            return encryptor.decrypt(token);
        }

        int workerCount() {
            return workers.size();
        }
    }
}
