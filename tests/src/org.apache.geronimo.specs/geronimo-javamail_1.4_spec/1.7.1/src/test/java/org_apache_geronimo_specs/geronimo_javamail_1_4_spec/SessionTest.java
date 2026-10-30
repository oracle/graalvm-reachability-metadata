/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_javamail_1_4_spec;

import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Provider;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.NewsAddress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionTest {

    @Test
    void discoversApplicationAndDefaultProvidersAndConstructsConfiguredTransport() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", "session-application");
        Session session = Session.getInstance(properties);

        assertThat(session.getProviders()).extracting(Provider::getProtocol)
                .contains("session-application", "session-default");
        Provider application = session.getProvider("session-application");
        assertThat(application.getType()).isEqualTo(Provider.Type.TRANSPORT);
        assertThat(application.getClassName()).isEqualTo(RecordingTransport.class.getName());
        assertThat(application.getVendor()).isEqualTo("Application");
        assertThat(session.getProvider("session-default").getVendor()).isEqualTo("Defaults");

        assertDelivery(session, session.getTransport(), "session-application",
                new InternetAddress("reader@example.org"));
    }

    @Test
    void routesAddressesUsingDefaultMapAndApplicationOverrides() throws Exception {
        Session session = Session.getInstance(new Properties());
        InternetAddress mailbox = new InternetAddress("reader@example.org");
        NewsAddress newsgroup = new NewsAddress("example.announcements");

        assertDelivery(session, session.getTransport(mailbox), "session-application", mailbox);
        assertDelivery(session, session.getTransport(newsgroup), "session-default", newsgroup);
    }

    private static void assertDelivery(Session session, Transport transport, String protocol, Address recipient)
            throws Exception {
        assertThat(transport).isInstanceOf(RecordingTransport.class);
        assertThat(transport.getURLName().getProtocol()).isEqualTo(protocol);
        assertThat(transport.isConnected()).isFalse();
        MimeMessage message = new MimeMessage(session);
        message.setSubject("Delivery notification");
        message.setText("Your report is ready.");
        try {
            transport.connect("localhost", "sender", "secret");
            assertThat(transport.isConnected()).isTrue();
            transport.sendMessage(message, new Address[] {recipient});

            RecordingTransport recording = (RecordingTransport) transport;
            assertThat(recording.deliveredMessage).isSameAs(message);
            assertThat(recording.deliveredMessage.getSubject()).isEqualTo("Delivery notification");
            assertThat(recording.deliveredRecipients).containsExactly(recipient);
        } finally {
            transport.close();
        }
        assertThat(transport.isConnected()).isFalse();
    }

    /// An application-provided transport registered through the JavaMail provider resources.
    public static final class RecordingTransport extends Transport {
        private Message deliveredMessage;
        private Address[] deliveredRecipients;

        public RecordingTransport(Session session, URLName urlName) {
            super(session, urlName);
        }

        @Override
        protected boolean protocolConnect(String host, int port, String user, String password) {
            return true;
        }

        @Override
        public void sendMessage(Message message, Address[] addresses) throws MessagingException {
            if (!isConnected()) {
                throw new MessagingException("Transport must be connected before delivery");
            }
            deliveredMessage = message;
            deliveredRecipients = addresses.clone();
        }
    }
}
