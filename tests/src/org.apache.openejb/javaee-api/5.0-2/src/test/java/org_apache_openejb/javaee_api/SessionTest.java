/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.NoSuchProviderException;
import javax.mail.Provider;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.URLName;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionTest {
    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();

    @AfterEach
    void restoreThreadContextClassLoader() {
        currentThread.setContextClassLoader(originalContextClassLoader);
    }

    @Test
    void loadsProvidersFromClasspathResourcesAndInstantiatesServices() throws NoSuchProviderException {
        currentThread.setContextClassLoader(SessionTest.class.getClassLoader());

        Session session = Session.getInstance(new Properties());

        Map<String, Provider> providersByProtocol = Arrays.stream(session.getProviders())
                .collect(Collectors.toMap(
                        Provider::getProtocol,
                        provider -> provider,
                        (left, right) -> right,
                        LinkedHashMap::new));

        assertThat(providersByProtocol).containsKeys("sessiontesttransport", "sessionteststore");
        assertThat(providersByProtocol.get("sessiontesttransport").getClassName()).isEqualTo(RecordingTransport.class.getName());
        assertThat(providersByProtocol.get("sessionteststore").getClassName()).isEqualTo(RecordingStore.class.getName());

        RecordingTransport transport = (RecordingTransport) session.getTransport("sessiontesttransport");
        assertThat(transport.getRecordedSession()).isSameAs(session);
        assertThat(transport.getRecordedUrlName()).isNull();

        RecordingStore store = (RecordingStore) session.getStore("sessionteststore");
        assertThat(store.getRecordedSession()).isSameAs(session);
        assertThat(store.getRecordedUrlName()).isNull();
    }

    @Test
    void loadsAddressMapResourcesWhenResolvingTransportForAddressTypes() throws NoSuchProviderException {
        currentThread.setContextClassLoader(SessionTest.class.getClassLoader());

        Session session = Session.getInstance(new Properties());

        RecordingTransport defaultMappedTransport =
                (RecordingTransport) session.getTransport(new SessionTestAddress("sessiontestdefault", "default@example.test"));
        assertThat(defaultMappedTransport.getRecordedSession()).isSameAs(session);

        RecordingTransport customMappedTransport =
                (RecordingTransport) session.getTransport(new SessionTestAddress("sessiontestcustom", "custom@example.test"));
        assertThat(customMappedTransport.getRecordedSession()).isSameAs(session);
    }

    public static final class SessionTestAddress extends Address {
        private final String type;
        private final String value;

        public SessionTestAddress(String type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SessionTestAddress)) {
                return false;
            }
            SessionTestAddress that = (SessionTestAddress) other;
            return type.equals(that.type) && value.equals(that.value);
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static final class RecordingStore extends Store {
        private final Session recordedSession;
        private final URLName recordedUrlName;

        public RecordingStore(Session session, URLName urlName) {
            super(session, urlName);
            this.recordedSession = session;
            this.recordedUrlName = urlName;
        }

        Session getRecordedSession() {
            return recordedSession;
        }

        URLName getRecordedUrlName() {
            return recordedUrlName;
        }

        @Override
        public Folder getDefaultFolder() {
            return null;
        }

        @Override
        public Folder getFolder(String name) {
            return null;
        }

        @Override
        public Folder getFolder(URLName url) {
            return null;
        }
    }

    public static final class RecordingTransport extends Transport {
        private final Session recordedSession;
        private final URLName recordedUrlName;

        public RecordingTransport(Session session, URLName urlName) {
            super(session, urlName);
            this.recordedSession = session;
            this.recordedUrlName = urlName;
        }

        Session getRecordedSession() {
            return recordedSession;
        }

        URLName getRecordedUrlName() {
            return recordedUrlName;
        }

        @Override
        public void sendMessage(Message message, Address[] addresses) {
        }
    }
}
