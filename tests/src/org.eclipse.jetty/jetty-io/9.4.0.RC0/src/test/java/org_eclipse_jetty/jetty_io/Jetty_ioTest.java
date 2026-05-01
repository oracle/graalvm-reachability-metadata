/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_io;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.jetty.io.ArrayByteBufferPool;
import org.eclipse.jetty.io.ByteArrayEndPoint;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.ChannelEndPoint;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.io.IdleTimeout;
import org.eclipse.jetty.io.LeakTrackingByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.io.RuntimeIOException;
import org.eclipse.jetty.io.SocketChannelEndPoint;
import org.eclipse.jetty.io.WriterOutputStream;
import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.io.ssl.SslHandshakeListener;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.util.thread.TimerScheduler;
import org.junit.jupiter.api.Test;

public class Jetty_ioTest {
    @Test
    void arrayByteBufferPoolReusesHeapAndDirectBuffers() {
        ArrayByteBufferPool pool = new ArrayByteBufferPool(0, 16, 64, 4);

        ByteBuffer heap = pool.acquire(13, false);
        assertFalse(heap.isDirect());
        assertTrue(heap.capacity() >= 13);
        BufferUtil.clearToFill(heap);
        heap.put("abc".getBytes(StandardCharsets.UTF_8));
        pool.release(heap);

        ByteBuffer reusedHeap = pool.acquire(8, false);
        assertFalse(reusedHeap.isDirect());
        assertEquals(0, reusedHeap.position());
        assertEquals(0, reusedHeap.limit());
        assertTrue(reusedHeap.capacity() >= 8);
        pool.release(reusedHeap);

        ByteBuffer direct = pool.acquire(18, true);
        assertTrue(direct.isDirect());
        assertTrue(direct.capacity() >= 18);
        pool.release(direct);

        pool.clear();
        ByteBuffer fresh = pool.acquire(4, false);
        assertFalse(fresh.isDirect());
        pool.release(fresh);
    }

    @Test
    void mappedPoolAndLeakTrackingPoolExposeExpectedBufferLifecycle() {
        MappedByteBufferPool mappedPool = new MappedByteBufferPool(16, 4);
        ByteBuffer heap = mappedPool.acquire(17, false);
        ByteBuffer direct = mappedPool.acquire(17, true);

        assertFalse(heap.isDirect());
        assertTrue(heap.capacity() >= 17);
        assertTrue(direct.isDirect());
        assertTrue(direct.capacity() >= 17);

        mappedPool.release(heap);
        mappedPool.release(direct);
        mappedPool.clear();

        LeakTrackingByteBufferPool trackingPool = new LeakTrackingByteBufferPool(new ArrayByteBufferPool());
        ByteBuffer tracked = trackingPool.acquire(32, false);
        assertNotNull(tracked);
        trackingPool.release(tracked);
        trackingPool.clearTracking();

        assertEquals(0, trackingPool.getLeakedAcquires());
        assertEquals(0, trackingPool.getLeakedReleases());
        assertEquals(0, trackingPool.getLeakedResources());
    }

    @Test
    void byteBufferPoolLeaseAggregatesAndRecyclesBuffers() {
        ArrayByteBufferPool pool = new ArrayByteBufferPool(0, 16, 64, 4);
        ByteBufferPool.Lease lease = new ByteBufferPool.Lease(pool);

        ByteBuffer first = lease.acquire(16, false);
        first.put("world".getBytes(StandardCharsets.UTF_8));
        first.flip();
        lease.append(first, true);

        ByteBuffer second = lease.acquire(16, false);
        second.put("hello ".getBytes(StandardCharsets.UTF_8));
        second.flip();
        lease.insert(0, second, true);

        List<ByteBuffer> buffers = lease.getByteBuffers();
        assertEquals(2, lease.getSize());
        assertEquals(11, lease.getTotalLength());
        assertEquals("hello ", BufferUtil.toString(buffers.get(0), StandardCharsets.UTF_8));
        assertEquals("world", BufferUtil.toString(buffers.get(1), StandardCharsets.UTF_8));

        lease.recycle();
        assertEquals(0, lease.getSize());
        assertEquals(0, lease.getTotalLength());
    }

    @Test
    void byteArrayEndPointFillsFlushesAndTracksShutdownState() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint("hello", 4);
        endPoint.setGrowOutput(true);

        ByteBuffer input = BufferUtil.allocate(16);
        assertEquals(5, endPoint.fill(input));
        assertEquals("hello", BufferUtil.toString(input, StandardCharsets.UTF_8));
        assertFalse(endPoint.hasMore());

        endPoint.addInput(" jetty", StandardCharsets.UTF_8);
        ByteBuffer moreInput = BufferUtil.allocate(16);
        assertEquals(6, endPoint.fill(moreInput));
        assertEquals(" jetty", BufferUtil.toString(moreInput, StandardCharsets.UTF_8));

        assertTrue(endPoint.flush(BufferUtil.toBuffer("out"), BufferUtil.toBuffer("put")));
        assertEquals("output", endPoint.getOutputString(StandardCharsets.UTF_8));
        assertEquals("output", endPoint.takeOutputString(StandardCharsets.UTF_8));
        assertEquals("", endPoint.takeOutputString(StandardCharsets.UTF_8));

        endPoint.addInputEOF();
        assertEquals(-1, endPoint.fill(BufferUtil.allocate(1)));
        assertTrue(endPoint.isInputShutdown());

        endPoint.shutdownOutput();
        assertTrue(endPoint.isOutputShutdown());
        endPoint.close();
        assertFalse(endPoint.isOpen());
    }

    @Test
    void byteArrayEndPointFillInterestAndWriteCallbacksComplete() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        endPoint.setGrowOutput(true);

        RecordingCallback readCallback = new RecordingCallback();
        endPoint.fillInterested(readCallback);
        assertTrue(endPoint.isFillInterested());
        endPoint.addInputAndExecute(BufferUtil.toBuffer("data"));
        assertTrue(readCallback.awaitSuccess());
        assertFalse(endPoint.isFillInterested());

        ByteBuffer input = BufferUtil.allocate(8);
        assertEquals(4, endPoint.fill(input));
        assertEquals("data", BufferUtil.toString(input, StandardCharsets.UTF_8));

        FutureCallback writeCallback = new FutureCallback();
        endPoint.write(writeCallback, BufferUtil.toBuffer("async"), BufferUtil.toBuffer(" write"));
        writeCallback.get(2, TimeUnit.SECONDS);
        assertEquals("async write", endPoint.takeOutputString(StandardCharsets.UTF_8));
    }

    @Test
    void byteArrayEndPointReportsEofAndRuntimeIoFailures() throws Exception {
        ByteArrayEndPoint endPoint = new ByteArrayEndPoint();
        endPoint.addInputEOF();

        assertEquals(-1, endPoint.fill(BufferUtil.allocate(1)));
        assertThrows(RuntimeIOException.class, () -> endPoint.addInput("after-eof"));

        endPoint.close();
        assertThrows(EofException.class, () -> endPoint.fill(BufferUtil.allocate(1)));
        assertThrows(IOException.class, () -> endPoint.flush(BufferUtil.toBuffer("closed")));
    }

    @Test
    void channelEndPointTransfersDataOverSocketChannel() throws Exception {
        TimerScheduler scheduler = new TimerScheduler();
        scheduler.start();
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

            try (SocketChannel client = SocketChannel.open((InetSocketAddress) server.getLocalAddress());
                    SocketChannel accepted = server.accept()) {
                ChannelEndPoint endPoint = new SocketChannelEndPoint(accepted, null, null, scheduler);

                assertTrue(client.write(BufferUtil.toBuffer("ping")) > 0);
                ByteBuffer input = BufferUtil.allocate(8);
                assertEquals(4, endPoint.fill(input));
                assertEquals("ping", BufferUtil.toString(input, StandardCharsets.UTF_8));

                assertTrue(endPoint.flush(BufferUtil.toBuffer("pong")));
                ByteBuffer reply = ByteBuffer.allocate(4);
                assertEquals(4, client.read(reply));
                reply.flip();
                assertEquals("pong", StandardCharsets.UTF_8.decode(reply).toString());

                client.shutdownOutput();
                assertEquals(-1, endPoint.fill(BufferUtil.allocate(1)));
                assertTrue(endPoint.isInputShutdown());

                endPoint.shutdownOutput();
                assertTrue(endPoint.isOutputShutdown());
                endPoint.close();
                assertFalse(endPoint.isOpen());
            }
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void idleTimeoutExpiresAfterConfiguredInactivity() throws Exception {
        TimerScheduler scheduler = new TimerScheduler();
        scheduler.start();
        try {
            RecordingIdleTimeout idleTimeout = new RecordingIdleTimeout(scheduler);
            idleTimeout.setIdleTimeout(100);
            idleTimeout.onOpen();

            assertTrue(idleTimeout.isOpen());
            assertEquals(100, idleTimeout.getIdleTimeout());
            assertTrue(idleTimeout.getIdleTimestamp() > 0);
            assertTrue(idleTimeout.awaitExpiration());
            assertNotNull(idleTimeout.expiration.get());
            assertTrue(idleTimeout.getIdleFor() >= 0);

            idleTimeout.onClose();
            assertFalse(idleTimeout.isOpen());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void writerOutputStreamDecodesBytesThroughWriter() throws Exception {
        StringWriter writer = new StringWriter();
        byte[] utf8Bytes = "UTF-8: \u03C0, \u0416, \u4E2D".getBytes(StandardCharsets.UTF_8);
        byte[] decoratedTail = "--tail--".getBytes(StandardCharsets.UTF_8);

        try (WriterOutputStream stream = new WriterOutputStream(writer, StandardCharsets.UTF_8.name())) {
            for (byte value : "ASCII and ".getBytes(StandardCharsets.US_ASCII)) {
                stream.write(value & 0xFF);
            }
            stream.write(utf8Bytes);
            stream.write('\n');
            stream.write(decoratedTail, 2, 4);
            stream.flush();
        }

        assertEquals("ASCII and UTF-8: \u03C0, \u0416, \u4E2D\ntail", writer.toString());
    }

    @Test
    void connectionListenerReceivesOpenCloseCallbacksAndTrafficAccessorsWork() {
        TestConnection connection = new TestConnection(200, 80, 3, 1);
        RecordingConnectionListener listener = new RecordingConnectionListener();

        connection.addListener(listener);
        connection.onOpen();
        assertSame(connection, listener.opened.get());
        assertNull(listener.closed.get());

        assertEquals(200, connection.getBytesIn());
        assertEquals(80, connection.getBytesOut());
        assertEquals(3, connection.getMessagesIn());
        assertEquals(1, connection.getMessagesOut());
        assertTrue(connection.getCreatedTimeStamp() <= System.currentTimeMillis());

        connection.close();
        assertSame(connection, listener.closed.get());

        connection.removeListener(listener);
        listener.opened.set(null);
        listener.closed.set(null);
        connection.onOpen();
        connection.onClose();
        assertNull(listener.opened.get());
        assertNull(listener.closed.get());
    }

    @Test
    void sslConnectionExposesEngineDecryptedEndpointAndHandshakeListeners() throws Exception {
        ByteArrayEndPoint networkEndPoint = new ByteArrayEndPoint();
        SSLEngine engine = SSLContext.getDefault().createSSLEngine("localhost", 8443);
        SslConnection sslConnection = new SslConnection(
                new ArrayByteBufferPool(), Runnable::run, networkEndPoint, engine);
        RecordingHandshakeListener listener = new RecordingHandshakeListener();
        SslHandshakeListener.Event event = new SslHandshakeListener.Event(engine);

        IOException handshakeFailure = new IOException("handshake-failed");
        listener.handshakeSucceeded(event);
        listener.handshakeFailed(event, handshakeFailure);
        assertSame(engine, event.getSSLEngine());
        assertSame(event, listener.successfulEvent.get());
        assertSame(event, listener.failedEvent.get());
        assertSame(handshakeFailure, listener.failure.get());

        sslConnection.addHandshakeListener(listener);
        assertTrue(sslConnection.removeHandshakeListener(listener));
        assertFalse(sslConnection.removeHandshakeListener(listener));
        assertSame(engine, sslConnection.getSSLEngine());
        assertSame(sslConnection, sslConnection.getDecryptedEndPoint().getSslConnection());

        sslConnection.setRenegotiationAllowed(false);
        assertFalse(sslConnection.isRenegotiationAllowed());
        sslConnection.setRenegotiationAllowed(true);
        assertTrue(sslConnection.isRenegotiationAllowed());

        sslConnection.getDecryptedEndPoint().setConnection(new TestConnection(0, 0, 0, 0));
        assertDoesNotThrow(sslConnection::close);
    }

    private static final class RecordingIdleTimeout extends IdleTimeout {
        private final CountDownLatch expirationLatch = new CountDownLatch(1);
        private final AtomicReference<TimeoutException> expiration = new AtomicReference<>();
        private boolean open;

        private RecordingIdleTimeout(TimerScheduler scheduler) {
            super(scheduler);
        }

        @Override
        public void onOpen() {
            open = true;
            super.onOpen();
        }

        @Override
        public void onClose() {
            open = false;
            super.onClose();
        }

        @Override
        protected void onIdleExpired(TimeoutException timeout) {
            expiration.set(timeout);
            expirationLatch.countDown();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        boolean awaitExpiration() throws InterruptedException {
            boolean expired = expirationLatch.await(2, TimeUnit.SECONDS);
            assertTrue(expired);
            return true;
        }
    }

    private static final class RecordingHandshakeListener implements SslHandshakeListener {
        private final AtomicReference<SslHandshakeListener.Event> successfulEvent = new AtomicReference<>();
        private final AtomicReference<SslHandshakeListener.Event> failedEvent = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void handshakeSucceeded(SslHandshakeListener.Event event) {
            successfulEvent.set(event);
        }

        @Override
        public void handshakeFailed(SslHandshakeListener.Event event, Throwable failure) {
            failedEvent.set(event);
            this.failure.set(failure);
        }
    }

    private static final class RecordingConnectionListener implements Connection.Listener {
        private final AtomicReference<Connection> opened = new AtomicReference<>();
        private final AtomicReference<Connection> closed = new AtomicReference<>();

        @Override
        public void onOpened(Connection connection) {
            opened.set(connection);
        }

        @Override
        public void onClosed(Connection connection) {
            closed.set(connection);
        }
    }

    private static final class RecordingCallback implements Callback {
        private final CountDownLatch successLatch = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        @Override
        public void succeeded() {
            successLatch.countDown();
        }

        @Override
        public void failed(Throwable x) {
            failure.set(x);
            successLatch.countDown();
        }

        boolean awaitSuccess() throws InterruptedException {
            boolean completed = successLatch.await(2, TimeUnit.SECONDS);
            assertTrue(completed);
            assertNull(failure.get());
            return true;
        }
    }

    private static final class TestConnection implements Connection {
        private final EndPoint endPoint = new ByteArrayEndPoint();
        private final long createdTimeStamp = System.currentTimeMillis() - 5;
        private final long bytesIn;
        private final long bytesOut;
        private final int messagesIn;
        private final int messagesOut;
        private final List<Connection.Listener> listeners = new ArrayList<>();

        private TestConnection(long bytesIn, long bytesOut, int messagesIn, int messagesOut) {
            this.bytesIn = bytesIn;
            this.bytesOut = bytesOut;
            this.messagesIn = messagesIn;
            this.messagesOut = messagesOut;
        }

        @Override
        public void addListener(Connection.Listener listener) {
            listeners.add(listener);
        }

        @Override
        public void removeListener(Connection.Listener listener) {
            listeners.remove(listener);
        }

        @Override
        public void onOpen() {
            listeners.forEach(listener -> listener.onOpened(this));
        }

        @Override
        public void onClose() {
            listeners.forEach(listener -> listener.onClosed(this));
        }

        @Override
        public EndPoint getEndPoint() {
            return endPoint;
        }

        @Override
        public void close() {
            onClose();
        }

        @Override
        public boolean onIdleExpired() {
            return true;
        }

        @Override
        public int getMessagesIn() {
            return messagesIn;
        }

        @Override
        public int getMessagesOut() {
            return messagesOut;
        }

        @Override
        public long getBytesIn() {
            return bytesIn;
        }

        @Override
        public long getBytesOut() {
            return bytesOut;
        }

        @Override
        public long getCreatedTimeStamp() {
            return createdTimeStamp;
        }
    }
}
