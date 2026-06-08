/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.angus.mail.iap.Protocol;
import org.eclipse.angus.mail.util.MailLogger;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

public class ProtocolTest {
    private static final int TIMEOUT_MILLIS = 5_000;

    @Test
    public void sslSocketChannelIsFoundThroughNamedWrappedSocketField() throws Exception {
        SocketChannel channel = connectAndReadChannel(NamedSocketFieldSslSocketFactory::newSocket);

        assertThat(channel).isNotNull();
    }

    @Test
    public void sslSocketChannelIsFoundThroughAssignableWrappedSocketField() throws Exception {
        SocketChannel channel = connectAndReadChannel(AssignableSocketFieldSslSocketFactory::newSocket);

        assertThat(channel).isNotNull();
    }

    private static SocketChannel connectAndReadChannel(SslSocketCreator socketCreator) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            serverSocket.setSoTimeout(TIMEOUT_MILLIS);
            Future<Void> accepted = executor.submit(() -> acceptProtocolConnection(serverSocket));

            Properties properties = new Properties();
            properties.setProperty("mail.test.connectiontimeout", Integer.toString(TIMEOUT_MILLIS));
            properties.setProperty("mail.test.timeout", Integer.toString(TIMEOUT_MILLIS));
            properties.setProperty("mail.test.usesocketchannels", "true");
            properties.setProperty("mail.test.ssl.checkserveridentity", "false");
            properties.setProperty("mail.test.socketFactory.fallback", "false");
            properties.put("mail.test.ssl.socketFactory", new DelegatingSslSocketFactory(socketCreator));

            ExposedProtocol protocol = new ExposedProtocol(serverSocket.getInetAddress().getHostAddress(),
                    serverSocket.getLocalPort(), properties);
            try {
                return protocol.getChannel();
            } finally {
                protocol.close();
                accepted.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Void acceptProtocolConnection(ServerSocket serverSocket) throws IOException {
        try (Socket socket = serverSocket.accept()) {
            socket.setSoTimeout(TIMEOUT_MILLIS);
            socket.getOutputStream().write("* OK test server ready\r\n".getBytes());
            socket.getOutputStream().flush();
        }
        return null;
    }

    private interface SslSocketCreator {
        SSLSocket create(Socket socket) throws IOException;
    }

    public static final class ExposedProtocol extends Protocol {
        private ExposedProtocol(String host, int port, Properties properties) throws Exception {
            super(host, port, properties, "mail.test", true,
                    new MailLogger(ProtocolTest.class, "DEBUG", false, System.out));
        }

        @Override
        public SocketChannel getChannel() {
            return super.getChannel();
        }

        private void close() {
            disconnect();
        }
    }

    public static final class DelegatingSslSocketFactory extends SSLSocketFactory {
        private final SslSocketCreator socketCreator;

        private DelegatingSslSocketFactory(SslSocketCreator socketCreator) {
            this.socketCreator = socketCreator;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return new String[] {"TLS_AES_128_GCM_SHA256"};
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return getDefaultCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return socketCreator.create(socket);
        }

        @Override
        public Socket createSocket(String host, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
            throw new UnsupportedOperationException();
        }
    }

    public static final class NamedSocketFieldSslSocketFactory {
        private static SSLSocket newSocket(Socket socket) {
            return new NamedSocketFieldSslSocket(socket);
        }
    }

    public static final class AssignableSocketFieldSslSocketFactory {
        private static SSLSocket newSocket(Socket socket) {
            return new AssignableSocketFieldSslSocket(socket);
        }
    }

    public static final class NamedSocketFieldSslSocket extends AbstractDelegatingSslSocket {
        private final Socket socket;

        private NamedSocketFieldSslSocket(Socket socket) {
            super(socket);
            this.socket = socket;
        }
    }

    public static final class AssignableSocketFieldSslSocket extends AbstractDelegatingSslSocket {
        private AssignableSocketFieldSslSocket(Socket socket) {
            super(socket);
        }
    }

    public abstract static class AbstractDelegatingSslSocket extends SSLSocket {
        private final Socket delegate;
        private boolean useClientMode = true;
        private boolean enableSessionCreation = true;
        private String[] enabledCipherSuites = new String[] {"TLS_AES_128_GCM_SHA256"};
        private String[] enabledProtocols = new String[] {"TLSv1.3"};

        private AbstractDelegatingSslSocket(Socket delegate) {
            this.delegate = delegate;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return delegate.getOutputStream();
        }

        @Override
        public SocketChannel getChannel() {
            return null;
        }

        @Override
        public InetAddress getInetAddress() {
            return delegate.getInetAddress();
        }

        @Override
        public InetAddress getLocalAddress() {
            return delegate.getLocalAddress();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }

        @Override
        public int getLocalPort() {
            return delegate.getLocalPort();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return delegate.getRemoteSocketAddress();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return delegate.getLocalSocketAddress();
        }

        @Override
        public boolean isConnected() {
            return delegate.isConnected();
        }

        @Override
        public boolean isBound() {
            return delegate.isBound();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void setSoTimeout(int timeout) throws SocketException {
            delegate.setSoTimeout(timeout);
        }

        @Override
        public int getSoTimeout() throws SocketException {
            return delegate.getSoTimeout();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[] {"TLS_AES_128_GCM_SHA256"};
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
            return new String[] {"TLSv1.3", "TLSv1.2"};
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
            throw new UnsupportedOperationException();
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
        }

        @Override
        public boolean getNeedClientAuth() {
            return false;
        }

        @Override
        public void setWantClientAuth(boolean want) {
        }

        @Override
        public boolean getWantClientAuth() {
            return false;
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
}
