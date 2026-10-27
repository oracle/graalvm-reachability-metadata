/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import io.nats.client.AuthHandler;
import io.nats.client.Connection;
import io.nats.client.ErrorListener;
import io.nats.client.Message;
import io.nats.client.NKey;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.Subscription;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Secures an order gateway with jnats authentication and authorization: NKey challenge signing,
 * user/password credentials, per-user subject permissions, and permission-violation errors.
 *
 * The order service holds a freshly generated user NKey whose public key the server trusts, and
 * signs the server nonce through an application auth handler. The billing service logs in with
 * a user name and password and may only read orders and publish invoices. Each service observes
 * the server rejecting what its permissions forbid through an error listener, and connections
 * with a wrong password or an unknown NKey are refused.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecuredOrderGatewayAuthTest {

    private static final String NATS_IMAGE = "nats:2.15.1";

    private static final String BILLING_USER = "billing";

    private static final String BILLING_PASSWORD = "invoice-s3cret";

    private final String containerName = "real-use-jnats-auth-" + UUID.randomUUID();

    private NKey orderServiceKey;

    private Path configDirectory;

    private String serverUrl;

    @BeforeAll
    void startServer() throws Exception {
        orderServiceKey = NKey.createUser(new SecureRandom());
        configDirectory = Files.createTempDirectory("real-use-jnats-auth");
        Path config = configDirectory.resolve("server.conf");
        Files.writeString(config, serverConfig(new String(orderServiceKey.getPublicKey())));
        configDirectory.toFile().setReadable(true, false);
        configDirectory.toFile().setExecutable(true, false);
        config.toFile().setReadable(true, false);

        int port = freePort();
        serverUrl = "nats://127.0.0.1:" + port;
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":4222", "-v", configDirectory + ":/etc/gateway:ro",
                NATS_IMAGE, "-c", "/etc/gateway/server.conf")
                .inheritIO()
                .start();
        assertThat(run.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(run.exitValue()).isZero();
        awaitServer();
    }

    @AfterAll
    void stopServer() throws Exception {
        Process remove = new ProcessBuilder("docker", "rm", "-f", containerName).inheritIO().start();
        remove.waitFor(60, TimeUnit.SECONDS);
        Files.deleteIfExists(configDirectory.resolve("server.conf"));
        Files.deleteIfExists(configDirectory);
    }

    @Test
    void signsNonceVerifiesSignaturesAndRestoresKeyFromSeed() throws Exception {
        char[] publicKey = orderServiceKey.getPublicKey();
        assertThat(orderServiceKey.getType()).isEqualTo(NKey.Type.USER);
        assertThat(publicKey[0]).isEqualTo('U');
        assertThat(NKey.isValidPublicUserKey(publicKey)).isTrue();
        assertThat(NKey.isValidPublicAccountKey(publicKey)).isFalse();
        assertThat(new String(orderServiceKey.getSeed())).startsWith("SU");

        byte[] nonce = "server-nonce-42".getBytes(StandardCharsets.UTF_8);
        byte[] signature = orderServiceKey.sign(nonce);
        assertThat(signature).hasSize(64);

        NKey verifier = NKey.fromPublicKey(publicKey);
        assertThat(verifier.verify(nonce, signature)).isTrue();
        assertThat(verifier.verify("tampered".getBytes(StandardCharsets.UTF_8), signature)).isFalse();

        NKey restored = NKey.fromSeed(orderServiceKey.getSeed());
        assertThat(restored.getPublicKey()).isEqualTo(publicKey);
        assertThat(restored.sign(nonce)).isEqualTo(signature);
    }

    @Test
    void routesOrdersBetweenAuthenticatedServicesWithinTheirPermissions() throws Exception {
        List<String> orderServiceErrors = new CopyOnWriteArrayList<>();
        List<String> billingErrors = new CopyOnWriteArrayList<>();
        NKeyAuthHandler orderServiceAuth = new NKeyAuthHandler(orderServiceKey);
        try (Connection orders = Nats.connect(orderServiceOptions(orderServiceAuth, orderServiceErrors));
             Connection billing = Nats.connect(billingOptions(BILLING_PASSWORD, billingErrors))) {
            assertThat(orders.getStatus()).isEqualTo(Connection.Status.CONNECTED);
            assertThat(orders.getServerInfo().isAuthRequired()).isTrue();
            assertThat(orderServiceAuth.signedNonces()).isEqualTo(1);
            assertThat(billing.getStatus()).isEqualTo(Connection.Status.CONNECTED);

            Subscription incomingOrders = billing.subscribe("orders.>");
            Subscription invoices = orders.subscribe("billing.invoices");
            billing.flush(Duration.ofSeconds(5));
            orders.flush(Duration.ofSeconds(5));

            orders.publish("orders.created", "order-1001".getBytes(StandardCharsets.UTF_8));
            Message order = incomingOrders.nextMessage(Duration.ofSeconds(5));
            assertThat(order).isNotNull();
            assertThat(order.getSubject()).isEqualTo("orders.created");
            assertThat(new String(order.getData(), StandardCharsets.UTF_8)).isEqualTo("order-1001");

            billing.publish("billing.invoices", "invoice-for-1001".getBytes(StandardCharsets.UTF_8));
            Message invoice = invoices.nextMessage(Duration.ofSeconds(5));
            assertThat(invoice).isNotNull();
            assertThat(new String(invoice.getData(), StandardCharsets.UTF_8)).isEqualTo("invoice-for-1001");

            billing.publish("orders.cancelled", "forged".getBytes(StandardCharsets.UTF_8));
            billing.flush(Duration.ofSeconds(5));
            billing.subscribe("admin.>");
            billing.flush(Duration.ofSeconds(5));
            orders.publish("billing.invoices", "forged".getBytes(StandardCharsets.UTF_8));
            orders.flush(Duration.ofSeconds(5));

            awaitErrors(billingErrors, 2);
            awaitErrors(orderServiceErrors, 1);
            assertThat(billingErrors).anySatisfy(error -> assertThat(error)
                    .contains("Permissions Violation for Publish to \"orders.cancelled\""));
            assertThat(billingErrors).anySatisfy(error -> assertThat(error)
                    .contains("Permissions Violation for Subscription to \"admin.>\""));
            assertThat(orderServiceErrors).singleElement().satisfies(error -> assertThat(error)
                    .contains("Permissions Violation for Publish to \"billing.invoices\""));
            assertThat(incomingOrders.nextMessage(Duration.ofMillis(500))).isNull();
            assertThat(invoices.nextMessage(Duration.ofMillis(500))).isNull();

            assertThat(billing.getStatus()).isEqualTo(Connection.Status.CONNECTED);
            assertThat(orders.getStatus()).isEqualTo(Connection.Status.CONNECTED);
        }
    }

    @Test
    void refusesWrongPasswordAndUnknownNKey() throws Exception {
        List<String> ignored = new CopyOnWriteArrayList<>();
        assertThatThrownBy(() -> Nats.connect(billingOptions("wrong-password", ignored)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Authorization Violation");

        NKey intruder = NKey.createUser(new SecureRandom());
        assertThat(intruder.getPublicKey()).isNotEqualTo(orderServiceKey.getPublicKey());
        assertThatThrownBy(() -> Nats.connect(orderServiceOptions(new NKeyAuthHandler(intruder), ignored)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Authorization Violation");
    }

    private Options orderServiceOptions(AuthHandler authHandler, List<String> errors) {
        return new Options.Builder()
                .server(serverUrl)
                .connectionName("order-service")
                .connectionTimeout(Duration.ofSeconds(2))
                .authHandler(authHandler)
                .errorListener(new RecordingErrorListener(errors))
                .build();
    }

    private Options billingOptions(String password, List<String> errors) {
        return new Options.Builder()
                .server(serverUrl)
                .connectionName("billing-service")
                .connectionTimeout(Duration.ofSeconds(2))
                .userInfo(BILLING_USER, password)
                .errorListener(new RecordingErrorListener(errors))
                .build();
    }

    private void awaitServer() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        IOException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = Nats.connect(billingOptions(BILLING_PASSWORD, new CopyOnWriteArrayList<>()))) {
                return;
            } catch (IOException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("NATS did not accept connections at " + serverUrl, lastFailure);
    }

    private static void awaitErrors(List<String> errors, int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (errors.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        assertThat(errors).hasSizeGreaterThanOrEqualTo(count);
    }

    private static String serverConfig(String orderServicePublicKey) {
        return """
                authorization {
                  users = [
                    {
                      nkey: "%s"
                      permissions: {
                        publish: ["orders.>"]
                        subscribe: ["billing.invoices", "_INBOX.>"]
                      }
                    }
                    {
                      user: "%s"
                      password: "%s"
                      permissions: {
                        publish: ["billing.>", "_INBOX.>"]
                        subscribe: ["orders.>", "_INBOX.>"]
                      }
                    }
                  ]
                }
                """.formatted(orderServicePublicKey, BILLING_USER, BILLING_PASSWORD);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /// Answers the server's connect challenge by signing its nonce with the service's user NKey.
    static final class NKeyAuthHandler implements AuthHandler {

        private final NKey key;

        private final AtomicInteger signedNonces = new AtomicInteger();

        NKeyAuthHandler(NKey key) {
            this.key = key;
        }

        @Override
        public byte[] sign(byte[] nonce) {
            signedNonces.incrementAndGet();
            try {
                return key.sign(nonce);
            } catch (GeneralSecurityException | IOException e) {
                throw new IllegalStateException("Cannot sign the server nonce", e);
            }
        }

        @Override
        public char[] getID() {
            try {
                return key.getPublicKey();
            } catch (GeneralSecurityException | IOException e) {
                throw new IllegalStateException("Cannot derive the public NKey", e);
            }
        }

        @Override
        public char[] getJWT() {
            return null;
        }

        int signedNonces() {
            return signedNonces.get();
        }
    }

    /// Records every protocol error the server reports on a connection.
    static final class RecordingErrorListener implements ErrorListener {

        private final List<String> errors;

        RecordingErrorListener(List<String> errors) {
            this.errors = errors;
        }

        @Override
        public void errorOccurred(Connection connection, String error) {
            errors.add(error);
        }
    }
}
