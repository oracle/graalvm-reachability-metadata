/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.imap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/// A real TLS provider whose layered sockets do not expose their transport channel directly.
final class TlsChannelSocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    private final boolean namedField;
    private SocketChannel channel;

    TlsChannelSocketFactory(SSLSocketFactory delegate, boolean namedField) {
        this.delegate = delegate;
        this.namedField = namedField;
    }

    SocketChannel channel() {
        return channel;
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        channel = socket.getChannel();
        SSLSocket tls = (SSLSocket) delegate.createSocket(socket, host, port, autoClose);
        return namedField ? new NamedChannelSocket(tls, socket) : new ChannelSocket(tls, socket);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
        return delegate.createSocket(host, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress host, int port, InetAddress localAddress, int localPort) throws IOException {
        return delegate.createSocket(host, port, localAddress, localPort);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    private static final class NamedChannelSocket extends ChannelSocket {
        private final Socket socket;

        private NamedChannelSocket(SSLSocket tls, Socket socket) {
            super(tls, socket);
            this.socket = socket;
        }
    }

    private static class ChannelSocket extends SSLSocket {
        private final SSLSocket tls;
        private final Socket transport;

        private ChannelSocket(SSLSocket tls, Socket transport) {
            this.tls = tls;
            this.transport = transport;
        }

        @Override
        public SocketChannel getChannel() {
            // Some TLS providers retain the channel only on the wrapped transport.
            return null;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return tls.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return tls.getOutputStream();
        }

        @Override
        public InetAddress getInetAddress() {
            return tls.getInetAddress();
        }

        @Override
        public InetAddress getLocalAddress() {
            return tls.getLocalAddress();
        }

        @Override
        public int getPort() {
            return tls.getPort();
        }

        @Override
        public int getLocalPort() {
            return tls.getLocalPort();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return tls.getRemoteSocketAddress();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return tls.getLocalSocketAddress();
        }

        @Override
        public boolean isConnected() {
            return tls.isConnected();
        }

        @Override
        public boolean isBound() {
            return tls.isBound();
        }

        @Override
        public boolean isClosed() {
            return tls.isClosed();
        }

        @Override
        public void setSoTimeout(int timeout) throws SocketException {
            tls.setSoTimeout(timeout);
        }

        @Override
        public int getSoTimeout() throws SocketException {
            return tls.getSoTimeout();
        }

        @Override
        public synchronized void close() throws IOException {
            tls.close();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return tls.getSupportedCipherSuites();
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return tls.getEnabledCipherSuites();
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
            tls.setEnabledCipherSuites(suites);
        }

        @Override
        public String[] getSupportedProtocols() {
            return tls.getSupportedProtocols();
        }

        @Override
        public String[] getEnabledProtocols() {
            return tls.getEnabledProtocols();
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
            tls.setEnabledProtocols(protocols);
        }

        @Override
        public SSLSession getSession() {
            return tls.getSession();
        }

        @Override
        public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
            tls.addHandshakeCompletedListener(listener);
        }

        @Override
        public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
            tls.removeHandshakeCompletedListener(listener);
        }

        @Override
        public void startHandshake() throws IOException {
            tls.startHandshake();
        }

        @Override
        public void setUseClientMode(boolean mode) {
            tls.setUseClientMode(mode);
        }

        @Override
        public boolean getUseClientMode() {
            return tls.getUseClientMode();
        }

        @Override
        public void setNeedClientAuth(boolean need) {
            tls.setNeedClientAuth(need);
        }

        @Override
        public boolean getNeedClientAuth() {
            return tls.getNeedClientAuth();
        }

        @Override
        public void setWantClientAuth(boolean want) {
            tls.setWantClientAuth(want);
        }

        @Override
        public boolean getWantClientAuth() {
            return tls.getWantClientAuth();
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
            tls.setEnableSessionCreation(flag);
        }

        @Override
        public boolean getEnableSessionCreation() {
            return tls.getEnableSessionCreation();
        }
    }
}
