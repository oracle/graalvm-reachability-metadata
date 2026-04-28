/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SslHandlerTest {
    @Test
    void ignoresReadExceptionFromSocketChannelSubclassAfterSslClose() {
        RecordingExceptionHandler exceptionRecorder = new RecordingExceptionHandler();
        SslHandler sslHandler = new SslHandler(new ClosingSslEngine());
        EmbeddedChannel channel = new EmbeddedChannel(sslHandler, exceptionRecorder);

        channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {21, 3, 3, 0, 1, 0}));
        Assertions.assertTrue(sslHandler.sslCloseFuture().isDone());

        IOException readFailureAfterClose = new IOException("read failed after close_notify");
        readFailureAfterClose.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(ReadableTransport.class.getName(), "read", "ReadableTransport.java", 1)
        });
        channel.pipeline().fireExceptionCaught(readFailureAfterClose);

        Assertions.assertFalse(exceptionRecorder.exceptionCaught);
        channel.finish();
    }

    private static final class RecordingExceptionHandler extends ChannelInboundHandlerAdapter {
        private boolean exceptionCaught;

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            exceptionCaught = true;
        }
    }

    private static final class ClosingSslEngine extends SSLEngine {
        private static final SSLSession SESSION = new MinimalSslSession();

        private boolean inboundDone;
        private boolean outboundDone;

        @Override
        public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) {
            return new SSLEngineResult(SSLEngineResult.Status.CLOSED,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, 0, 0);
        }

        @Override
        public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) {
            int consumed = src.remaining();
            src.position(src.limit());
            inboundDone = true;
            return new SSLEngineResult(SSLEngineResult.Status.CLOSED,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, consumed, 0);
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
            return new String[0];
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return new String[0];
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
        }

        @Override
        public String[] getSupportedProtocols() {
            return new String[0];
        }

        @Override
        public String[] getEnabledProtocols() {
            return new String[0];
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
        }

        @Override
        public SSLSession getSession() {
            return SESSION;
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
        }

        @Override
        public boolean getUseClientMode() {
            return false;
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
        }

        @Override
        public boolean getEnableSessionCreation() {
            return false;
        }
    }

    private static final class MinimalSslSession implements SSLSession {
        @Override
        public byte[] getId() {
            return new byte[0];
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
            throw new SSLPeerUnverifiedException("peer is not verified");
        }

        @Override
        public Certificate[] getLocalCertificates() {
            return new Certificate[0];
        }

        @Override
        public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
            throw new SSLPeerUnverifiedException("peer is not verified");
        }

        @Override
        public Principal getLocalPrincipal() {
            return null;
        }

        @Override
        public String getCipherSuite() {
            return "NULL";
        }

        @Override
        public String getProtocol() {
            return "NONE";
        }

        @Override
        public String getPeerHost() {
            return "";
        }

        @Override
        public int getPeerPort() {
            return -1;
        }

        @Override
        public int getPacketBufferSize() {
            return 32;
        }

        @Override
        public int getApplicationBufferSize() {
            return 32;
        }
    }

    private abstract static class ReadableTransport extends SocketChannel {
        ReadableTransport() {
            super(SelectorProvider.provider());
        }

        @Override
        public SocketChannel bind(SocketAddress local) throws IOException {
            return this;
        }

        @Override
        public <T> SocketChannel setOption(SocketOption<T> name, T value) throws IOException {
            return this;
        }

        @Override
        public <T> T getOption(SocketOption<T> name) throws IOException {
            return null;
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return Collections.emptySet();
        }

        @Override
        public SocketChannel shutdownInput() throws IOException {
            return this;
        }

        @Override
        public SocketChannel shutdownOutput() throws IOException {
            return this;
        }

        @Override
        public Socket socket() {
            return null;
        }

        @Override
        public boolean isConnected() {
            return false;
        }

        @Override
        public boolean isConnectionPending() {
            return false;
        }

        @Override
        public boolean connect(SocketAddress remote) throws IOException {
            return false;
        }

        @Override
        public boolean finishConnect() throws IOException {
            return false;
        }

        @Override
        public SocketAddress getRemoteAddress() throws IOException {
            return null;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return -1;
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return -1L;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            return 0;
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return 0L;
        }

        @Override
        public SocketAddress getLocalAddress() throws IOException {
            return null;
        }

        @Override
        protected void implCloseSelectableChannel() throws IOException {
        }

        @Override
        protected void implConfigureBlocking(boolean block) throws IOException {
        }
    }
}
