/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.security.auth.x500.X500Principal;

import com.sun.mail.util.SocketFetcher;
import org.junit.jupiter.api.Test;

public class SocketFetcherTest {
    @Test
    void loadsSocketFactoryClassWithContextClassLoader() throws Exception {
        RecordingSocketFactory.reset();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicReference<Throwable> serverFailure = new AtomicReference<>();
            Thread serverThread = new Thread(() -> acceptOneConnection(serverSocket, serverFailure));
            serverThread.start();

            Properties props = new Properties();
            props.setProperty("mail.test.socketFactory.class", RecordingSocketFactory.class.getName());
            props.setProperty("mail.test.connectiontimeout", "5000");

            Thread thread = Thread.currentThread();
            ClassLoader originalContextClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(SocketFetcherTest.class.getClassLoader());
            try (Socket socket = SocketFetcher.getSocket("127.0.0.1", serverSocket.getLocalPort(), props,
                    "mail.test")) {
                assertThat(socket.isConnected()).isTrue();
            } finally {
                thread.setContextClassLoader(originalContextClassLoader);
            }

            serverThread.join(5000);
            assertThat(serverThread.isAlive()).isFalse();
            assertThat(serverFailure.get()).isNull();
            assertThat(RecordingSocketFactory.wasUsed()).isTrue();
        }
    }

    @Test
    void loadsSocketFactoryClassWithCallerClassLoaderFallback() throws Exception {
        RecordingSocketFactory.reset();

        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            AtomicReference<Throwable> serverFailure = new AtomicReference<>();
            Thread serverThread = new Thread(() -> acceptOneConnection(serverSocket, serverFailure));
            serverThread.start();

            Properties props = new Properties();
            props.setProperty("mail.test.socketFactory.class", RecordingSocketFactory.class.getName());
            props.setProperty("mail.test.connectiontimeout", "5000");

            Thread thread = Thread.currentThread();
            ClassLoader originalContextClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(new HidingClassLoader(originalContextClassLoader,
                    RecordingSocketFactory.class.getName()));
            try (Socket socket = SocketFetcher.getSocket("127.0.0.1", serverSocket.getLocalPort(), props,
                    "mail.test")) {
                assertThat(socket.isConnected()).isTrue();
            } finally {
                thread.setContextClassLoader(originalContextClassLoader);
            }

            serverThread.join(5000);
            assertThat(serverThread.isAlive()).isFalse();
            assertThat(serverFailure.get()).isNull();
            assertThat(RecordingSocketFactory.wasUsed()).isTrue();
        }
    }

    @Test
    void checksServerIdentityWithCertificateHostnameChecker() throws Exception {
        Properties props = new Properties();
        props.put("mail.test.ssl.socketFactory", new TestSSLSocketFactory("localhost"));
        props.setProperty("mail.test.ssl.checkserveridentity", "true");

        try (Socket upgradedSocket = SocketFetcher.startTLS(new Socket(), "localhost", props, "mail.test")) {
            assertThat(upgradedSocket).isInstanceOf(TestSSLSocket.class);
        }
    }

    private static void acceptOneConnection(ServerSocket serverSocket, AtomicReference<Throwable> serverFailure) {
        try (Socket ignored = serverSocket.accept()) {
            assertThat(ignored.isConnected()).isTrue();
        } catch (Throwable throwable) {
            serverFailure.set(throwable);
        }
    }

    private static final class HidingClassLoader extends ClassLoader {
        private final String hiddenClassName;

        HidingClassLoader(ClassLoader parent, String hiddenClassName) {
            super(parent);
            this.hiddenClassName = hiddenClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (hiddenClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }

    public static final class RecordingSocketFactory extends SocketFactory {
        private static boolean used;

        public static SocketFactory getDefault() {
            return new RecordingSocketFactory();
        }

        static void reset() {
            used = false;
        }

        static boolean wasUsed() {
            return used;
        }

        @Override
        public Socket createSocket() {
            used = true;
            return new Socket();
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            used = true;
            return new Socket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            used = true;
            return new Socket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            used = true;
            return new Socket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            used = true;
            return new Socket(address, port, localAddress, localPort);
        }
    }

    private static final class TestSSLSocketFactory extends SSLSocketFactory {
        private final String certificateDnsName;

        TestSSLSocketFactory(String certificateDnsName) {
            this.certificateDnsName = certificateDnsName;
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
            if (autoClose) {
                socket.close();
            }
            return new TestSSLSocket(certificateDnsName);
        }

        @Override
        public Socket createSocket(String host, int port) {
            return new TestSSLSocket(certificateDnsName);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
            return new TestSSLSocket(certificateDnsName);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) {
            return new TestSSLSocket(certificateDnsName);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) {
            return new TestSSLSocket(certificateDnsName);
        }
    }

    private static final class TestSSLSocket extends SSLSocket {
        private final SSLSession session;
        private String[] enabledProtocols = new String[] {"TLSv1.3"};
        private String[] enabledCipherSuites = new String[] {"TLS_AES_128_GCM_SHA256"};
        private boolean useClientMode = true;
        private boolean needClientAuth;
        private boolean wantClientAuth;
        private boolean enableSessionCreation = true;

        TestSSLSocket(String certificateDnsName) {
            this.session = new TestSSLSession(certificateDnsName);
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
            return session;
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

    private static final class TestSSLSession implements SSLSession {
        private final X509Certificate certificate;

        TestSSLSession(String certificateDnsName) {
            this.certificate = new TestX509Certificate(certificateDnsName);
        }

        @Override
        public byte[] getId() {
            return new byte[] {1};
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return null;
        }

        @Override
        public long getCreationTime() {
            return 0L;
        }

        @Override
        public long getLastAccessedTime() {
            return 0L;
        }

        @Override
        public void invalidate() {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public void putValue(String name, Object value) {
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public void removeValue(String name) {
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            return new Certificate[] {certificate};
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public javax.security.cert.X509Certificate[] getPeerCertificateChain() {
            return new javax.security.cert.X509Certificate[0];
        }

        @Override
        public Principal getPeerPrincipal() {
            return certificate.getSubjectX500Principal();
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public String getCipherSuite() {
            return "TLS_AES_128_GCM_SHA256";
        }

        @Override
        public String getProtocol() {
            return "TLSv1.3";
        }

        @Override
        public String getPeerHost() {
            return "localhost";
        }

        @Override
        public int getPeerPort() {
            return 443;
        }

        @Override
        public int getPacketBufferSize() {
            return 16_384;
        }

        @Override
        public int getApplicationBufferSize() {
            return 16_384;
        }
    }

    private static final class TestX509Certificate extends X509Certificate {
        private final String dnsName;
        private final X500Principal principal;

        TestX509Certificate(String dnsName) {
            this.dnsName = dnsName;
            this.principal = new X500Principal("CN=" + dnsName);
        }

        @Override
        public Collection<List<?>> getSubjectAlternativeNames() {
            return List.of(List.of(Integer.valueOf(2), dnsName));
        }

        @Override
        public X500Principal getSubjectX500Principal() {
            return principal;
        }

        @Override
        public X500Principal getIssuerX500Principal() {
            return principal;
        }

        @Override
        public void checkValidity() {
        }

        @Override
        public void checkValidity(Date date) {
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public BigInteger getSerialNumber() {
            return BigInteger.ONE;
        }

        @Override
        public Principal getIssuerDN() {
            return principal;
        }

        @Override
        public Principal getSubjectDN() {
            return principal;
        }

        @Override
        public Date getNotBefore() {
            return new Date(0L);
        }

        @Override
        public Date getNotAfter() {
            return new Date(Long.MAX_VALUE);
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "NONE";
        }

        @Override
        public String getSigAlgOID() {
            return "1.2.3.4";
        }

        @Override
        public byte[] getSigAlgParams() {
            return new byte[0];
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return new boolean[0];
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return new boolean[0];
        }

        @Override
        public boolean[] getKeyUsage() {
            return new boolean[0];
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public void verify(PublicKey key) {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) {
        }

        @Override
        public String toString() {
            return "TestX509Certificate[" + dnsName + "]";
        }

        @Override
        public PublicKey getPublicKey() {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return Set.of();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return new byte[0];
        }
    }
}
