/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;

public class SslHandlerTest {
    @Test
    void ignoresClosedSslConnectionExceptionFromLoadableSocketChannelImplementation() throws Exception {
        SslHandler sslHandler = new SslHandler(new ClosingInboundSslEngine(new FixedSizeSslSession()));
        ExceptionRecorder exceptionRecorder = new ExceptionRecorder();
        EmbeddedChannel channel = new EmbeddedChannel(sslHandler, exceptionRecorder);

        try {
            assertThat(channel.writeInbound(
                    Unpooled.wrappedBuffer(new byte[] {21, 3, 3, 0, 1, 0}))).isFalse();
            assertThat(sslHandler.sslCloseFuture().isDone()).isTrue();

            IOException cause = new IOException("peer closed after close_notify");
            cause.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement(NioReadable.class.getName(), "read", "SslHandlerTest.java", 1)
            });

            channel.pipeline().fireExceptionCaught(cause);

            assertThat(exceptionRecorder.cause.get()).isNull();
            assertThat(channel.isOpen()).isFalse();
        } finally {
            channel.finish();
        }
    }

    private abstract static class NioReadable extends SocketChannel {
        NioReadable() {
            super(null);
        }
    }

    private static final class ExceptionRecorder extends ChannelInboundHandlerAdapter {
        private final AtomicReference<Throwable> cause = new AtomicReference<Throwable>();

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            this.cause.set(cause);
        }
    }

    private static final class FixedSizeSslSession implements SSLSession {
        @Override
        public int getApplicationBufferSize() {
            return 1024;
        }

        @Override
        public String getCipherSuite() {
            return "TLS_FAKE_WITH_NULL_NULL";
        }

        @Override
        public long getCreationTime() {
            return 0;
        }

        @Override
        public byte[] getId() {
            return new byte[0];
        }

        @Override
        public long getLastAccessedTime() {
            return 0;
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return null;
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public int getPacketBufferSize() {
            return 1024;
        }

        @Override
        public X509Certificate[] getPeerCertificateChain() throws SSLPeerUnverifiedException {
            return null;
        }

        @Override
        public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
            return null;
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
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            return null;
        }

        @Override
        public String getProtocol() {
            return "TLSv1.2";
        }

        @Override
        public SSLSessionContext getSessionContext() {
            return null;
        }

        @Override
        public Object getValue(String name) {
            return null;
        }

        @Override
        public String[] getValueNames() {
            return new String[0];
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
        public void removeValue(String name) {
        }
    }

    private static final class ClosingInboundSslEngine extends SSLEngine {
        private final SSLSession session;
        private boolean inboundDone;
        private boolean outboundDone;
        private boolean useClientMode;
        private boolean needClientAuth;
        private boolean wantClientAuth;
        private boolean enableSessionCreation = true;

        private ClosingInboundSslEngine(SSLSession session) {
            this.session = session;
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
            int consumed = src.remaining();
            src.position(src.limit());
            inboundDone = true;
            return new SSLEngineResult(
                    SSLEngineResult.Status.CLOSED,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                    consumed,
                    0);
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length)
                throws SSLException {
            return unwrap(src, dsts[offset]);
        }

        @Override
        public SSLEngineResult wrap(ByteBuffer src, ByteBuffer dst) throws SSLException {
            return new SSLEngineResult(
                    SSLEngineResult.Status.CLOSED,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING,
                    0,
                    0);
        }

        @Override
        public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst)
                throws SSLException {
            return wrap(srcs[offset], dst);
        }

        @Override
        public Runnable getDelegatedTask() {
            return null;
        }

        @Override
        public void closeInbound() throws SSLException {
            inboundDone = true;
        }

        @Override
        public boolean isInboundDone() {
            return inboundDone;
        }

        @Override
        public void closeOutbound() {
            outboundDone = true;
        }

        @Override
        public boolean isOutboundDone() {
            return outboundDone;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return new String[] {"TLS_FAKE_WITH_NULL_NULL"};
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return getSupportedCipherSuites();
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
        }

        @Override
        public String[] getSupportedProtocols() {
            return new String[] {"TLSv1.2"};
        }

        @Override
        public String[] getEnabledProtocols() {
            return getSupportedProtocols();
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
        }

        @Override
        public SSLSession getSession() {
            return session;
        }

        @Override
        public void beginHandshake() throws SSLException {
        }

        @Override
        public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
            return SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
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
}
