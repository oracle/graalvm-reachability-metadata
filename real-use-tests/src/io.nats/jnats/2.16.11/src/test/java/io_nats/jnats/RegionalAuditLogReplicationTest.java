/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.PurgeOptions;
import io.nats.client.api.AccountStatistics;
import io.nats.client.api.MessageInfo;
import io.nats.client.api.Mirror;
import io.nats.client.api.PurgeResponse;
import io.nats.client.api.Source;
import io.nats.client.api.SourceInfo;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import io.nats.client.api.StreamInfoOptions;
import io.nats.client.api.Subject;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Replicates and administers JetStream streams through the jnats stream topology and
 * maintenance API: sources, mirrors, subject-filtered stream info, direct message get,
 * message deletion, filtered purge, stream updates, and account statistics.
 *
 * Two regional audit streams receive events; a global stream sources all of the EU region
 * and only the US payment events, and a backup stream mirrors the EU region. An auditor then
 * reads per-subject counts, walks messages by subject, erases one record, purges old login
 * events while keeping the latest, tightens the stream limits, and checks account usage.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RegionalAuditLogReplicationTest {

    private static final String NATS_IMAGE = "nats:2.15.0";

    private static final String EU = "AUDIT_EU";

    private static final String US = "AUDIT_US";

    private static final String GLOBAL = "AUDIT_GLOBAL";

    private static final String BACKUP = "AUDIT_EU_BACKUP";

    private final String containerName = "real-use-jnats-audit-" + UUID.randomUUID();

    private String serverUrl;

    @BeforeAll
    void startServer() throws Exception {
        int port = freePort();
        serverUrl = "nats://127.0.0.1:" + port;
        Process run = new ProcessBuilder("docker", "run", "-d", "--rm", "--name", containerName,
                "-p", port + ":4222", NATS_IMAGE, "-js")
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
    }

    @Test
    void replicatesAndMaintainsRegionalAuditStreams() throws Exception {
        try (Connection connection = connect()) {
            JetStreamManagement management = connection.jetStreamManagement();
            management.addStream(regional(EU, "audit.eu.>"));
            management.addStream(regional(US, "audit.us.>"));

            StreamInfo global = management.addStream(StreamConfiguration.builder()
                    .name(GLOBAL)
                    .description("All EU events and US payments")
                    .storageType(StorageType.Memory)
                    .sources(Source.builder().sourceName(EU).build(),
                            Source.builder().sourceName(US).filterSubject("audit.us.payments.>").build())
                    .allowDirect(true)
                    .build());
            assertThat(global.getConfiguration().getSources())
                    .extracting(Source::getSourceName)
                    .containsExactlyInAnyOrder(EU, US);

            StreamInfo backup = management.addStream(StreamConfiguration.builder()
                    .name(BACKUP)
                    .storageType(StorageType.Memory)
                    .mirror(Mirror.builder().sourceName(EU).build())
                    .build());
            assertThat(backup.getConfiguration().getMirror().getSourceName()).isEqualTo(EU);

            JetStream jetStream = connection.jetStream();
            publish(jetStream, "audit.eu.logins.alice", "alice", "login from Paris");
            publish(jetStream, "audit.eu.logins.alice", "alice", "login from Lyon");
            publish(jetStream, "audit.eu.payments.order-7", "bob", "charged 40 EUR");
            publish(jetStream, "audit.us.logins.carol", "carol", "login from Austin");
            publish(jetStream, "audit.us.payments.order-9", "carol", "charged 15 USD");
            publish(jetStream, "audit.eu.logins.alice", "alice", "login from Nice");

            StreamInfo replicated = awaitMessages(management, GLOBAL, 5);
            assertThat(replicated.getSourceInfos())
                    .extracting(SourceInfo::getName)
                    .containsExactlyInAnyOrder(EU, US);
            assertThat(replicated.getSourceInfos()).allSatisfy(source -> assertThat(source.getError()).isNull());
            StreamInfo mirrored = awaitMessages(management, BACKUP, 4);
            assertThat(mirrored.getMirrorInfo().getName()).isEqualTo(EU);
            assertThat(mirrored.getMirrorInfo().getLag()).isZero();

            StreamInfo bySubject = management.getStreamInfo(GLOBAL, StreamInfoOptions.allSubjects());
            Map<String, Long> counts = bySubject.getStreamState().getSubjects().stream()
                    .collect(Collectors.toMap(Subject::getName, Subject::getCount));
            assertThat(counts).containsOnly(
                    Map.entry("audit.eu.logins.alice", 3L),
                    Map.entry("audit.eu.payments.order-7", 1L),
                    Map.entry("audit.us.payments.order-9", 1L));
            StreamInfo payments = management.getStreamInfo(GLOBAL,
                    StreamInfoOptions.filterSubjects("audit.*.payments.>"));
            assertThat(payments.getStreamState().getSubjects())
                    .extracting(Subject::getName)
                    .containsExactlyInAnyOrder("audit.eu.payments.order-7", "audit.us.payments.order-9");

            MessageInfo firstLogin = management.getFirstMessage(GLOBAL, "audit.eu.logins.alice");
            assertThat(text(firstLogin)).isEqualTo("login from Paris");
            assertThat(firstLogin.getHeaders().getFirst("Audit-Actor")).isEqualTo("alice");
            MessageInfo secondLogin = management.getNextMessage(GLOBAL, firstLogin.getSeq() + 1,
                    "audit.eu.logins.alice");
            assertThat(text(secondLogin)).isEqualTo("login from Lyon");
            MessageInfo lastLogin = management.getLastMessage(GLOBAL, "audit.eu.logins.alice");
            assertThat(text(lastLogin)).isEqualTo("login from Nice");
            MessageInfo usPayment = management.getLastMessage(GLOBAL, "audit.us.payments.order-9");
            assertThat(text(management.getMessage(GLOBAL, usPayment.getSeq()))).isEqualTo("charged 15 USD");
            assertThat(text(management.getLastMessage(BACKUP, "audit.eu.payments.order-7")))
                    .isEqualTo("charged 40 EUR");

            assertThat(management.deleteMessage(GLOBAL, secondLogin.getSeq(), true)).isTrue();
            JetStreamApiException erased = catchThrowableOfType(
                    () -> management.getMessage(GLOBAL, secondLogin.getSeq()), JetStreamApiException.class);
            assertThat(erased.getErrorCode()).isEqualTo(404);
            StreamInfo withDeleted = management.getStreamInfo(GLOBAL, StreamInfoOptions.deletedDetails());
            assertThat(withDeleted.getStreamState().getDeleted()).containsExactly(secondLogin.getSeq());
            assertThat(withDeleted.getStreamState().getMsgCount()).isEqualTo(4);

            publish(jetStream, "audit.eu.logins.alice", "alice", "login from Lille");
            awaitMessages(management, GLOBAL, 5);
            PurgeResponse purged = management.purgeStream(GLOBAL, PurgeOptions.builder()
                    .subject("audit.eu.logins.alice")
                    .keep(1)
                    .build());
            assertThat(purged.isSuccess()).isTrue();
            assertThat(purged.getPurged()).isEqualTo(2);
            assertThat(text(management.getFirstMessage(GLOBAL, "audit.eu.logins.alice")))
                    .isEqualTo("login from Lille");

            StreamInfo tightened = management.updateStream(StreamConfiguration.builder(
                            management.getStreamInfo(US).getConfiguration())
                    .description("US audit events, last 100 per subject")
                    .maxMessagesPerSubject(100)
                    .build());
            assertThat(tightened.getConfiguration().getMaxMsgsPerSubject()).isEqualTo(100);
            assertThat(tightened.getConfiguration().getDescription())
                    .isEqualTo("US audit events, last 100 per subject");

            assertThat(management.getStreamNames("audit.us.logins.carol")).containsExactly(US);
            assertThat(management.getStreams()).extracting(info -> info.getConfiguration().getName())
                    .containsExactlyInAnyOrder(EU, US, GLOBAL, BACKUP);

            AccountStatistics account = management.getAccountStatistics();
            assertThat(account.getStreams()).isEqualTo(4);
            assertThat(account.getMemory()).isPositive();
            assertThat(account.getApi().getTotal()).isPositive();

            for (String stream : new String[] {BACKUP, GLOBAL, US, EU}) {
                assertThat(management.deleteStream(stream)).isTrue();
            }
            assertThat(management.getStreamNames()).isEmpty();
        }
    }

    private static StreamConfiguration regional(String name, String subjects) {
        return StreamConfiguration.builder()
                .name(name)
                .subjects(subjects)
                .storageType(StorageType.Memory)
                .build();
    }

    private static void publish(JetStream jetStream, String subject, String actor, String event) throws Exception {
        jetStream.publish(NatsMessage.builder()
                .subject(subject)
                .headers(new Headers().add("Audit-Actor", actor))
                .data(event, StandardCharsets.UTF_8)
                .build());
    }

    private static String text(MessageInfo message) {
        return new String(message.getData(), StandardCharsets.UTF_8);
    }

    private static StreamInfo awaitMessages(JetStreamManagement management, String stream, long expected)
            throws Exception {
        long deadline = System.currentTimeMillis() + 30_000L;
        StreamInfo info = management.getStreamInfo(stream);
        while (info.getStreamState().getMsgCount() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L);
            info = management.getStreamInfo(stream);
        }
        assertThat(info.getStreamState().getMsgCount()).as("messages in %s", stream).isEqualTo(expected);
        return info;
    }

    private Connection connect() throws Exception {
        Options options = new Options.Builder()
                .server(serverUrl)
                .connectionTimeout(Duration.ofSeconds(2))
                .build();
        return Nats.connect(options);
    }

    private void awaitServer() throws Exception {
        long deadline = System.currentTimeMillis() + 120_000L;
        IOException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try (Connection probe = connect()) {
                return;
            } catch (IOException e) {
                lastFailure = e;
                Thread.sleep(500L);
            }
        }
        throw new IllegalStateException("NATS did not accept connections at " + serverUrl, lastFailure);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
