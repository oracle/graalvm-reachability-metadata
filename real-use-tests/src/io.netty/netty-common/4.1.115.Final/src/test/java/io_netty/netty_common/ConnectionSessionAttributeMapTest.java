/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.DefaultAttributeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the attribute-map subsystem of netty-common: `AttributeKey` constants interned in
 * their global constant pool, `DefaultAttributeMap` holders with lazily created `Attribute`
 * slots, and the atomic `set`, `setIfAbsent`, `getAndSet` and `compareAndSet` updates on them.
 *
 * A chat gateway keeps per-connection state the way a Netty channel does: every connection is
 * an attribute map, the gateway declares typed keys once at startup, a login handshake binds the
 * user only if no other handshake already did, concurrent message handlers bump a sequence
 * number with compare-and-set, and logout clears the user while the other attributes survive.
 */
class ConnectionSessionAttributeMapTest {

    private static final long WAIT_SECONDS = 10;

    private static final AttributeKey<String> USER =
            AttributeKey.valueOf(ChatGateway.class, "user");

    private static final AttributeKey<Long> SEQUENCE =
            AttributeKey.valueOf(ChatGateway.class, "sequence");

    private static final AttributeKey<List<String>> ROOMS =
            AttributeKey.valueOf(ChatGateway.class, "rooms");

    private ExecutorService handlerThreads;

    @BeforeEach
    void startHandlers() {
        handlerThreads = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void stopHandlers() throws InterruptedException {
        handlerThreads.shutdownNow();
        assertThat(handlerThreads.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void keysAreInternedConstantsNamedAfterTheirOwner() {
        String userKeyName = ChatGateway.class.getName() + "#user";

        assertThat(USER.name()).isEqualTo(userKeyName);
        assertThat(AttributeKey.<String>valueOf(userKeyName)).isSameAs(USER);
        assertThat(AttributeKey.exists(userKeyName)).isTrue();
        assertThat(AttributeKey.exists(ChatGateway.class.getName() + "#never-declared")).isFalse();

        assertThat(USER.id()).isNotEqualTo(SEQUENCE.id()).isNotEqualTo(ROOMS.id());
        assertThat(USER.toString()).isEqualTo(userKeyName);

        String exclusiveName = ChatGateway.class.getName() + "#exclusive-token";
        AttributeKey<String> exclusive = AttributeKey.newInstance(exclusiveName);
        assertThat(exclusive.name()).isEqualTo(exclusiveName);
        assertThatThrownBy(() -> AttributeKey.newInstance(exclusiveName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(exclusiveName);
        assertThat(AttributeKey.<String>valueOf(exclusiveName)).isSameAs(exclusive);
        // Constants order by identity hash, so only the antisymmetry of the order is stable.
        assertThat(USER.compareTo(USER)).isZero();
        assertThat(USER.compareTo(exclusive)).isNotZero();
        assertThat(Integer.signum(exclusive.compareTo(USER)))
                .isEqualTo(-Integer.signum(USER.compareTo(exclusive)));
    }

    @Test
    void connectionAttributesAreCreatedLazilyAndKeptPerConnection() {
        ChatGateway gateway = new ChatGateway();
        Connection alice = gateway.accept("10.0.0.1:50001");
        Connection bob = gateway.accept("10.0.0.2:50002");

        assertThat(alice.hasAttr(USER)).isFalse();
        Attribute<String> aliceUser = alice.attr(USER);
        assertThat(alice.hasAttr(USER)).isTrue();
        assertThat(aliceUser.key()).isSameAs(USER);
        assertThat(aliceUser.get()).isNull();
        assertThat(alice.attr(USER)).isSameAs(aliceUser);

        assertThat(gateway.login(alice, "alice")).isTrue();
        assertThat(gateway.login(bob, "bob")).isTrue();
        gateway.join(alice, "general");
        gateway.join(alice, "random");
        gateway.join(bob, "general");

        assertThat(gateway.userOf(alice)).isEqualTo("alice");
        assertThat(gateway.userOf(bob)).isEqualTo("bob");
        assertThat(alice.attr(ROOMS).get()).containsExactly("general", "random");
        assertThat(bob.attr(ROOMS).get()).containsExactly("general");
        assertThat(alice.attr(ROOMS)).isNotSameAs(bob.attr(ROOMS));
        assertThat(bob.hasAttr(SEQUENCE)).isFalse();
    }

    @Test
    void secondLoginOnTheSameConnectionIsRejected() {
        ChatGateway gateway = new ChatGateway();
        Connection connection = gateway.accept("10.0.0.3:50003");

        assertThat(gateway.login(connection, "carol")).isTrue();
        assertThat(gateway.login(connection, "mallory")).isFalse();
        assertThat(gateway.userOf(connection)).isEqualTo("carol");

        Attribute<String> user = connection.attr(USER);
        assertThat(user.compareAndSet("mallory", "eve")).isFalse();
        assertThat(user.compareAndSet("carol", "carol-renamed")).isTrue();
        assertThat(gateway.userOf(connection)).isEqualTo("carol-renamed");
    }

    @Test
    void concurrentHandshakesBindExactlyOneUser() throws Exception {
        ChatGateway gateway = new ChatGateway();
        Connection connection = gateway.accept("10.0.0.4:50004");
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<Boolean>> outcomes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String candidate = "user-" + i;
            outcomes.add(handlerThreads.submit(() -> {
                startGate.await();
                return gateway.login(connection, candidate);
            }));
        }
        startGate.countDown();

        int winners = 0;
        for (Future<Boolean> outcome : outcomes) {
            if (outcome.get(WAIT_SECONDS, TimeUnit.SECONDS)) {
                winners++;
            }
        }
        assertThat(winners).isEqualTo(1);
        assertThat(gateway.userOf(connection)).startsWith("user-");
    }

    @Test
    void concurrentHandlersAssignGaplessSequenceNumbers() throws Exception {
        ChatGateway gateway = new ChatGateway();
        Connection connection = gateway.accept("10.0.0.5:50005");
        int handlers = 4;
        int messagesPerHandler = 250;
        CountDownLatch startGate = new CountDownLatch(1);

        List<Future<List<Long>>> assigned = new ArrayList<>();
        for (int h = 0; h < handlers; h++) {
            Callable<List<Long>> handler = () -> {
                startGate.await();
                List<Long> numbers = new ArrayList<>(messagesPerHandler);
                for (int m = 0; m < messagesPerHandler; m++) {
                    numbers.add(gateway.nextSequence(connection));
                }
                return numbers;
            };
            assigned.add(handlerThreads.submit(handler));
        }
        startGate.countDown();

        List<Long> all = new ArrayList<>();
        for (Future<List<Long>> numbers : assigned) {
            all.addAll(numbers.get(WAIT_SECONDS, TimeUnit.SECONDS));
        }
        int total = handlers * messagesPerHandler;
        assertThat(all).hasSize(total).doesNotHaveDuplicates();
        assertThat(all).allSatisfy(n -> assertThat(n).isBetween(1L, (long) total));
        assertThat(connection.attr(SEQUENCE).get()).isEqualTo((long) total);
    }

    @Test
    void logoutClearsTheUserButKeepsTheOtherAttributes() {
        ChatGateway gateway = new ChatGateway();
        Connection connection = gateway.accept("10.0.0.6:50006");
        gateway.login(connection, "dave");
        gateway.join(connection, "support");
        gateway.nextSequence(connection);

        assertThat(gateway.logout(connection)).isEqualTo("dave");
        assertThat(gateway.userOf(connection)).isNull();
        assertThat(gateway.logout(connection)).isNull();
        assertThat(connection.attr(ROOMS).get()).containsExactly("support");
        assertThat(connection.attr(SEQUENCE).get()).isEqualTo(1L);

        assertThat(gateway.login(connection, "dave-again")).isTrue();
        assertThat(gateway.userOf(connection)).isEqualTo("dave-again");
    }

    /// A client connection whose state lives in the attribute map it extends.
    static final class Connection extends DefaultAttributeMap {

        private final String remoteAddress;

        Connection(String remoteAddress) {
            this.remoteAddress = remoteAddress;
        }

        @Override
        public String toString() {
            return "Connection(" + remoteAddress + ")";
        }
    }

    /// Gateway logic that reads and writes connection state only through `AttributeMap`.
    static final class ChatGateway {

        Connection accept(String remoteAddress) {
            return new Connection(remoteAddress);
        }

        boolean login(AttributeMap connection, String user) {
            return connection.attr(USER).setIfAbsent(user) == null;
        }

        String userOf(AttributeMap connection) {
            return connection.attr(USER).get();
        }

        String logout(AttributeMap connection) {
            return connection.attr(USER).getAndSet(null);
        }

        void join(AttributeMap connection, String room) {
            Attribute<List<String>> rooms = connection.attr(ROOMS);
            List<String> joined = rooms.get();
            if (joined == null) {
                joined = new ArrayList<>();
                rooms.set(joined);
            }
            joined.add(room);
        }

        long nextSequence(AttributeMap connection) {
            Attribute<Long> sequence = connection.attr(SEQUENCE);
            while (true) {
                Long current = sequence.get();
                Long next = current == null ? 1L : current + 1;
                if (sequence.compareAndSet(current, next)) {
                    return next;
                }
            }
        }
    }
}
