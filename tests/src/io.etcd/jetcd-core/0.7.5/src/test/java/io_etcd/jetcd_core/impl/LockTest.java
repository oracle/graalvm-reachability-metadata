/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_etcd.jetcd_core.impl;

import com.google.common.base.Charsets;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Lock;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.lock.LockResponse;
import io.etcd.jetcd.test.EtcdClusterExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("resource")
// `@org.junit.jupiter.api.Timeout(value = 2, unit = TimeUnit.MINUTES)` can't be used in the nativeTest GraalVM CE 22.3
public class LockTest {
    @RegisterExtension
    public static final EtcdClusterExtension cluster = EtcdClusterExtension.builder()
            .withNodes(3)
            .build();
    private Lock lockClient;
    private static Lease leaseClient;
    private Set<ByteSequence> locksToRelease;
    private static final ByteSequence SAMPLE_NAME = ByteSequence.from("sample_name", Charsets.UTF_8);
    private static final ByteSequence namespace = ByteSequence.from("test-ns/", Charsets.UTF_8);
    private static final ByteSequence namespace2 = ByteSequence.from("test-ns2/", Charsets.UTF_8);

    @BeforeAll
    public static void setUp() {
        Client client = TestUtil.client(cluster).build();
        leaseClient = client.getLeaseClient();
    }

    @BeforeEach
    public void setUpEach() {
        locksToRelease = new HashSet<>();
    }

    @AfterEach
    public void tearDownEach() throws Exception {
        for (ByteSequence lockKey : locksToRelease) {
            lockClient.unlock(lockKey).get();
        }
    }

    private void initializeLockCLient(boolean useNamespace) {
        Client client = useNamespace
                ? TestUtil.client(cluster).namespace(namespace).build()
                : TestUtil.client(cluster).build();

        this.lockClient = client.getLockClient();
    }

    static Stream<Arguments> parameters() {
        return Stream.of(Arguments.of(true), Arguments.of(false));
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testLockWithoutLease(boolean useNamespace) throws Exception {
        initializeLockCLient(useNamespace);
        CompletableFuture<LockResponse> feature = lockClient.lock(SAMPLE_NAME, 0);
        LockResponse response = feature.get();
        locksToRelease.add(response.getKey());
        assertThat(response.getHeader()).isNotNull();
        assertThat(response.getKey()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testLockWithNotExistingLease(boolean useNamespace) {
        Throwable exception = assertThrows(ExecutionException.class, () -> {
            initializeLockCLient(useNamespace);
            CompletableFuture<LockResponse> feature = lockClient.lock(SAMPLE_NAME, 123456);
            LockResponse response = feature.get();
            locksToRelease.add(response.getKey());
        });
        assertThat(exception.getMessage().contains("etcdserver: requested lease not found")).isTrue();
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testLockWithLease(boolean useNamespace) throws Exception {
        initializeLockCLient(useNamespace);
        long lease = grantLease(5);
        CompletableFuture<LockResponse> feature = lockClient.lock(SAMPLE_NAME, lease);
        LockResponse response = feature.get();
        long startMillis = System.currentTimeMillis();
        CompletableFuture<LockResponse> feature2 = lockClient.lock(SAMPLE_NAME, 0);
        LockResponse response2 = feature2.get();
        long time = System.currentTimeMillis() - startMillis;
        assertThat(response2.getKey()).isNotEqualTo(response.getKey());
        assertThat(time >= 4500 && time <= 6000).withFailMessage(String.format("Lease not runned out after 5000ms, was %dms", time)).isTrue();
        locksToRelease.add(response.getKey());
        locksToRelease.add(response2.getKey());
    }

    @ParameterizedTest
    @MethodSource("parameters")
    public void testLockAndUnlock(boolean useNamespace) throws Exception {
        initializeLockCLient(useNamespace);
        long lease = grantLease(20);
        CompletableFuture<LockResponse> feature = lockClient.lock(SAMPLE_NAME, lease);
        LockResponse response = feature.get();
        lockClient.unlock(response.getKey()).get();
        long startTime = System.currentTimeMillis();
        CompletableFuture<LockResponse> feature2 = lockClient.lock(SAMPLE_NAME, 0);
        LockResponse response2 = feature2.get();
        long time = System.currentTimeMillis() - startTime;
        locksToRelease.add(response2.getKey());
        assertThat(response2.getKey()).isNotEqualTo(response.getKey());
        assertThat(time <= 500).withFailMessage(String.format("Lease not unlocked, wait time was too long (%dms)", time))
                .isTrue();
    }

    @Test
    public void testLockSegregationByNamespaces() throws Exception {
        initializeLockCLient(false);
        Client clientWithNamespace = TestUtil.client(cluster).namespace(namespace).build();
        Lock lockClientWithNamespace = clientWithNamespace.getLockClient();
        long lease = grantLease(5);
        CompletableFuture<LockResponse> feature = lockClientWithNamespace.lock(SAMPLE_NAME, lease);
        LockResponse response = feature.get();
        assertThat(response.getKey().startsWith(SAMPLE_NAME)).isTrue();
        ByteSequence nsKey = ByteSequence.from(namespace.concat(response.getKey()).getBytes());
        lockClient.unlock(nsKey).get();
        lease = grantLease(30);
        CompletableFuture<LockResponse> feature2 = lockClientWithNamespace.lock(SAMPLE_NAME, lease);
        LockResponse response2 = feature2.get();
        long timestamp2 = System.currentTimeMillis();
        long startTime = System.currentTimeMillis();
        assertThat(response2.getKey().startsWith(SAMPLE_NAME)).isTrue();
        assertThat(response2.getKey()).isNotEqualTo(response.getKey());
        assertThat((timestamp2 - startTime) <= 1000)
                .withFailMessage(String.format("Lease not unlocked, wait time was too long (%dms)", timestamp2 - startTime))
                .isTrue();
        locksToRelease.add(ByteSequence.from(namespace.concat(response2.getKey()).getBytes()));
        lease = grantLease(5);
        Client clientWithNamespace2 = TestUtil.client(cluster).namespace(namespace2).build();
        Lock lockClientWithNamespace2 = clientWithNamespace2.getLockClient();
        CompletableFuture<LockResponse> feature3 = lockClientWithNamespace2.lock(SAMPLE_NAME, lease);
        LockResponse response3 = feature3.get();
        long timestamp3 = System.currentTimeMillis();
        assertThat(response3.getKey().startsWith(SAMPLE_NAME)).isTrue();
        assertThat(response3.getKey()).isNotEqualTo(response2.getKey());
        assertThat((timestamp3 - timestamp2) <= 1000)
                .withFailMessage(String.format("wait time for requiring the lock was too long (%dms)", timestamp3 - timestamp2))
                .isTrue();
        locksToRelease.add(ByteSequence.from(namespace2.concat(response3.getKey()).getBytes()));
    }

    private static long grantLease(long ttl) throws Exception {
        CompletableFuture<LeaseGrantResponse> feature = leaseClient.grant(ttl);
        LeaseGrantResponse response = feature.get();
        return response.getID();
    }
}
