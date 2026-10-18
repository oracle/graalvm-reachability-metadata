/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSSecurityException;
import javax.jms.JMSSecurityRuntimeException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises authenticated JMS connections to a secured broker: credentials passed to
 * `createConnection(user, password)` and `createContext(user, password)`, set on the factory,
 * or carried by the `user`/`password` connector URL parameters, `JMSSecurityException` and
 * `JMSSecurityRuntimeException` for rejected logins, role grants for send, browse and consume,
 * and the validated user the broker stamps on every accepted message.
 *
 * A branch ledger is secured by role: tellers post entries, auditors browse and collect them,
 * and nobody else may connect at all. Denied operations are left out on purpose: on JDK 25,
 * Artemis 2.28 audits every authorization failure through `Subject.getSubject`, which fails.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TellerLedgerSecurityTest {

    static {
        // Like the Artemis distribution, keep the broker audit log off: it is not the subject here.
        System.setProperty("org.slf4j.simpleLogger.log.org.apache.activemq.audit", "warn");
    }

    private static final String LEDGER = "branch.ledger";

    private static final String TELLER_PASSWORD = "counter-42";

    private static final String AUDITOR_PASSWORD = "books-7";

    /** The property the broker sets to the authenticated sender when it populates validated users. */
    private static final String VALIDATED_USER = "_AMQ_VALIDATED_USER";

    private ActiveMQServer broker;

    private String url;

    private ActiveMQConnectionFactory connectionFactory;

    @BeforeAll
    void startBroker() throws Exception {
        url = "tcp://127.0.0.1:" + freePort();
        Role teller = new Role("teller", true, false, false, false, false, false, false, false, false, false);
        Role auditor = new Role("auditor", false, true, false, false, false, false, false, true, false, false);
        Configuration configuration = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(true)
                .setPopulateValidatedUser(true)
                .addAcceptorConfiguration("netty", url)
                .putSecurityRoles("branch.#", Set.of(teller, auditor))
                .addQueueConfiguration(new QueueConfiguration(LEDGER)
                        .setAddress(LEDGER)
                        .setRoutingType(RoutingType.ANYCAST));
        BranchDirectory directory = new BranchDirectory(Map.of(
                "teller", new BranchDirectory.Account(TELLER_PASSWORD, Set.of("teller")),
                "auditor", new BranchDirectory.Account(AUDITOR_PASSWORD, Set.of("auditor"))));
        broker = ActiveMQServers.newActiveMQServer(
                configuration, ManagementFactory.getPlatformMBeanServer(), directory, false);
        broker.start();
        assertThat(broker.waitForActivation(30, TimeUnit.SECONDS)).isTrue();
        connectionFactory = new ActiveMQConnectionFactory(url);
    }

    @AfterAll
    void stopBroker() throws Exception {
        if (connectionFactory != null) {
            connectionFactory.close();
        }
        if (broker != null) {
            broker.stop();
        }
    }

    @Test
    void rejectsAnonymousUnknownAndWrongCredentials() {
        assertThatThrownBy(() -> connectionFactory.createConnection().close())
                .isInstanceOf(JMSSecurityException.class);
        assertThatThrownBy(() -> connectionFactory.createConnection("teller", "guess").close())
                .isInstanceOf(JMSSecurityException.class);
        assertThatThrownBy(() -> connectionFactory.createContext("intruder", TELLER_PASSWORD).close())
                .isInstanceOf(JMSSecurityRuntimeException.class);
    }

    @Test
    void tellerPostsEntriesThatTheAuditorCollects() throws Exception {
        String tellerUrl = url + "?user=teller&password=" + TELLER_PASSWORD;
        try (ActiveMQConnectionFactory tellerTerminal = new ActiveMQConnectionFactory(tellerUrl);
                Connection teller = tellerTerminal.createConnection()) {
            Session session = teller.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue ledger = session.createQueue(LEDGER);
            try (MessageProducer counter = session.createProducer(ledger)) {
                counter.send(session.createTextMessage("deposit 120.00"));
                counter.send(session.createTextMessage("withdrawal 40.00"));
            }
        }

        try (JMSContext teller = connectionFactory.createContext("teller", TELLER_PASSWORD)) {
            teller.createProducer().send(teller.createQueue(LEDGER), "deposit 15.50");
        }

        try (ActiveMQConnectionFactory auditTerminal = new ActiveMQConnectionFactory(url)) {
            auditTerminal.setUser("auditor");
            auditTerminal.setPassword(AUDITOR_PASSWORD);
            try (Connection auditor = auditTerminal.createConnection()) {
                auditor.start();
                Session session = auditor.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Queue ledger = session.createQueue(LEDGER);

                try (QueueBrowser review = session.createBrowser(ledger)) {
                    List<String> reviewed = new ArrayList<>();
                    Enumeration<?> entries = review.getEnumeration();
                    while (entries.hasMoreElements()) {
                        reviewed.add(((TextMessage) entries.nextElement()).getText());
                    }
                    assertThat(reviewed).containsExactly("deposit 120.00", "withdrawal 40.00", "deposit 15.50");
                }

                try (MessageConsumer collector = session.createConsumer(ledger)) {
                    List<String> collected = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        TextMessage entry = (TextMessage) collector.receive(10_000);
                        assertThat(entry).isNotNull();
                        assertThat(entry.getStringProperty(VALIDATED_USER)).isEqualTo("teller");
                        collected.add(entry.getText());
                    }
                    assertThat(collected).containsExactly("deposit 120.00", "withdrawal 40.00", "deposit 15.50");
                    assertThat(collector.receiveNoWait()).isNull();
                }
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /**
     * The branch's user directory, plugged into the broker as its security manager; it returns
     * the validated user name so the broker can stamp it on accepted messages.
     */
    private static final class BranchDirectory implements ActiveMQSecurityManager3 {

        private record Account(String password, Set<String> roles) {
        }

        private final Map<String, Account> accounts;

        private BranchDirectory(Map<String, Account> accounts) {
            this.accounts = accounts;
        }

        @Override
        public boolean validateUser(String user, String password) {
            return account(user, password) != null;
        }

        @Override
        public boolean validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType) {
            return isGranted(account(user, password), roles, checkType);
        }

        @Override
        public String validateUser(String user, String password, RemotingConnection connection) {
            return account(user, password) == null ? null : user;
        }

        @Override
        public String validateUserAndRole(String user, String password, Set<Role> roles, CheckType checkType,
                String address, RemotingConnection connection) {
            return isGranted(account(user, password), roles, checkType) ? user : null;
        }

        private Account account(String user, String password) {
            Account account = user == null ? null : accounts.get(user);
            return account != null && account.password().equals(password) ? account : null;
        }

        private static boolean isGranted(Account account, Set<Role> roles, CheckType checkType) {
            return account != null && roles.stream()
                    .anyMatch(role -> account.roles().contains(role.getName()) && checkType.hasRole(role));
        }
    }
}
