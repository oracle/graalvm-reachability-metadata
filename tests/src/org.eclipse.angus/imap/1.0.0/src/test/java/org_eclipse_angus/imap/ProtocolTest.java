/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.imap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import com.sun.mail.iap.Protocol;
import com.sun.mail.util.MailLogger;
import org.junit.jupiter.api.Test;

public class ProtocolTest {
    private static final String IMAP_PREFIX = "mail.imap";
    private static final String SERVER_GREETING = "* OK IMAP server ready\r\n";

    @Test
    void socketChannelCanBeFoundThroughFieldNamedSocket() throws Exception {
        RecordingSocketFactory socketFactory =
            new RecordingSocketFactory(SocketNamedFieldSSLSocket::new);

        try {
            Protocol protocol = protocolUsing(socketFactory);

            assertThat(protocol.getChannel()).isSameAs(socketFactory.lastSocketChannel());
        } finally {
            socketFactory.closeLastSocket();
        }
    }

    @Test
    void socketChannelCanBeFoundThroughSocketTypedField() throws Exception {
        RecordingSocketFactory socketFactory =
            new RecordingSocketFactory(SocketTypedFieldSSLSocket::new);

        try {
            Protocol protocol = protocolUsing(socketFactory);

            assertThat(protocol.getChannel()).isSameAs(socketFactory.lastSocketChannel());
        } finally {
            socketFactory.closeLastSocket();
        }
    }

    private static Protocol protocolUsing(SocketFactory socketFactory) throws Exception {
        Properties properties = new Properties();
        properties.put(IMAP_PREFIX + ".socketFactory", socketFactory);
        properties.put(IMAP_PREFIX + ".ssl.socketFactory", socketFactory);
        properties.setProperty(IMAP_PREFIX + ".socketFactory.fallback", "false");
        return new Protocol("localhost", 993, properties, IMAP_PREFIX, true, logger());
    }

    private static MailLogger logger() {
        return new MailLogger(ProtocolTest.class, "DEBUG", false, System.out);
    }

    @FunctionalInterface
    private interface ScriptedSSLSocketSupplier {
        ScriptedSSLSocket create() throws IOException;
    }

    private static class RecordingSocketFactory extends SocketFactory {
        private final ScriptedSSLSocketSupplier socketSupplier;
        private ScriptedSSLSocket lastSocket;

        RecordingSocketFactory(ScriptedSSLSocketSupplier socketSupplier) {
            this.socketSupplier = socketSupplier;
        }

        SocketChannel lastSocketChannel() {
            return lastSocket.channelSocket().getChannel();
        }

        void closeLastSocket() throws IOException {
            if (lastSocket != null) {
                lastSocket.close();
            }
        }

        @Override
        public Socket createSocket() throws IOException {
            return newSocket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return newSocket();
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
                throws IOException {
            return newSocket();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return newSocket();
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                int localPort) throws IOException {
            return newSocket();
        }

        private Socket newSocket() throws IOException {
            lastSocket = socketSupplier.create();
            return lastSocket;
        }
    }

    private abstract static class ScriptedSSLSocket extends SSLSocket {
        private final InputStream input = new ByteArrayInputStream(
            SERVER_GREETING.getBytes(StandardCharsets.US_ASCII));
        private final OutputStream output = new ByteArrayOutputStream();
        private boolean useClientMode = true;
        private boolean enableSessionCreation = true;
        private boolean needClientAuth;
        private boolean wantClientAuth;
        private String[] enabledCipherSuites = new String[] {"TLS_FAKE_WITH_NULL_NULL"};
        private String[] enabledProtocols = new String[] {"TLSv1.3"};

        abstract Socket channelSocket();

        @Override
        public SocketChannel getChannel() {
            return null;
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public InetAddress getInetAddress() {
            return InetAddress.getLoopbackAddress();
        }

        @Override
        public synchronized void close() throws IOException {
            channelSocket().close();
            input.close();
            output.close();
        }

        @Override
        public void connect(SocketAddress endpoint) {
        }

        @Override
        public void connect(SocketAddress endpoint, int timeout) {
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return enabledCipherSuites.clone();
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return enabledCipherSuites.clone();
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
            enabledCipherSuites = suites.clone();
        }

        @Override
        public String[] getSupportedProtocols() {
            return enabledProtocols.clone();
        }

        @Override
        public String[] getEnabledProtocols() {
            return enabledProtocols.clone();
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
            enabledProtocols = protocols.clone();
        }

        @Override
        public SSLSession getSession() {
            return null;
        }

        @Override
        public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        }

        @Override
        public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        }

        @Override
        public void startHandshake() {
        }

        @Override
        public void setUseClientMode(boolean mode) {
            useClientMode = mode;
        }

        @Override
        public boolean getUseClientMode() {
            return useClientMode;
        }

        @Override
        public void setNeedClientAuth(boolean need) {
            needClientAuth = need;
        }

        @Override
        public boolean getNeedClientAuth() {
            return needClientAuth;
        }

        @Override
        public void setWantClientAuth(boolean want) {
            wantClientAuth = want;
        }

        @Override
        public boolean getWantClientAuth() {
            return wantClientAuth;
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
            enableSessionCreation = flag;
        }

        @Override
        public boolean getEnableSessionCreation() {
            return enableSessionCreation;
        }
    }

    private static class SocketNamedFieldSSLSocket extends ScriptedSSLSocket {
        private final Socket socket;

        SocketNamedFieldSSLSocket() throws IOException {
            socket = SocketChannel.open().socket();
        }

        @Override
        Socket channelSocket() {
            return socket;
        }
    }

    private static class SocketTypedFieldSSLSocket extends ScriptedSSLSocket {
        private final Socket delegate;

        SocketTypedFieldSSLSocket() throws IOException {
            delegate = SocketChannel.open().socket();
        }

        @Override
        Socket channelSocket() {
            return delegate;
        }
    }
}
