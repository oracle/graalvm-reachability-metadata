/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_xnio.xnio_nio;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.xnio.ChannelPipe;
import org.xnio.FileAccess;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.SocketAddressBuffer;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;

import static org.assertj.core.api.Assertions.assertThat;

public class Xnio_nioTest {
    private static final int TIMEOUT_SECONDS = 5;

    @Test
    void providerCreatesWorkerAndOpensFiles() throws Exception {
        Xnio xnio = Xnio.getInstance("nio");
        assertThat(xnio.getName()).isEqualTo("nio");

        Path file = Files.createTempFile("xnio-nio", ".txt");
        try (FileChannel channel = xnio.openFile(file.toFile(), FileAccess.READ_WRITE)) {
            ByteBuffer written = StandardCharsets.UTF_8.encode("file-channel");
            while (written.hasRemaining()) {
                assertThat(channel.write(written)).isGreaterThanOrEqualTo(0);
            }
            channel.position(0);

            ByteBuffer read = ByteBuffer.allocate("file-channel".length());
            while (read.hasRemaining() && channel.read(read) != -1) {
                // Continue reading until the payload is complete.
            }
            read.flip();
            assertThat(StandardCharsets.UTF_8.decode(read).toString()).isEqualTo("file-channel");
        } finally {
            Files.deleteIfExists(file);
        }

        XnioWorker worker = createWorker();
        try {
            assertThat(worker.getName()).startsWith("xnio-nio-test");
            assertThat(worker.getIoThreadCount()).isEqualTo(1);
        } finally {
            closeWorker(worker);
        }
    }

    @Test
    void streamServerAcceptsClientAndTransfersBytes() throws Exception {
        XnioWorker worker = createWorker();
        AcceptingChannel<StreamConnection> server = null;
        StreamConnection client = null;
        StreamConnection accepted = null;
        try {
            InetSocketAddress bindAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            OptionMap serverOptions = OptionMap.builder()
                    .set(Options.REUSE_ADDRESSES, true)
                    .set(Options.TCP_NODELAY, true)
                    .getMap();
            server = worker.createStreamConnectionServer(bindAddress, null, serverOptions);
            CountDownLatch acceptedLatch = new CountDownLatch(1);
            AtomicReference<StreamConnection> acceptedReference = new AtomicReference<>();
            AtomicReference<IOException> acceptFailure = new AtomicReference<>();
            server.getAcceptSetter().set(channel -> acceptFirstConnection(
                    channel,
                    acceptedReference,
                    acceptFailure,
                    acceptedLatch));
            server.resumeAccepts();

            InetSocketAddress serverAddress = server.getLocalAddress(InetSocketAddress.class);
            assertThat(serverAddress.getPort()).isPositive();
            assertThat(server.supportsOption(Options.TCP_NODELAY)).isTrue();
            assertThat(server.getOption(Options.TCP_NODELAY)).isTrue();

            IoFuture<StreamConnection> clientFuture = worker.openStreamConnection(
                    serverAddress,
                    null,
                    OptionMap.create(Options.TCP_NODELAY, true));
            client = getFutureResult(clientFuture);
            accepted = awaitAcceptedConnection(acceptedLatch, acceptedReference, acceptFailure);

            writeFully(client.getSinkChannel(), "client-to-server");
            assertThat(readFully(accepted.getSourceChannel(), "client-to-server".length()))
                    .isEqualTo("client-to-server");

            writeFully(accepted.getSinkChannel(), "server-to-client");
            assertThat(readFully(client.getSourceChannel(), "server-to-client".length()))
                    .isEqualTo("server-to-client");
        } finally {
            closeQuietly(accepted);
            closeQuietly(client);
            closeQuietly(server);
            closeWorker(worker);
        }
    }

    @Test
    void fullDuplexPipeConnectionTransfersInBothDirections() throws Exception {
        XnioWorker worker = createWorker();
        StreamConnection left = null;
        StreamConnection right = null;
        try {
            ChannelPipe<StreamConnection, StreamConnection> pipe = worker.createFullDuplexPipeConnection();
            left = pipe.getLeftSide();
            right = pipe.getRightSide();

            writeFully(left.getSinkChannel(), "left-to-right");
            assertThat(readFully(right.getSourceChannel(), "left-to-right".length()))
                    .isEqualTo("left-to-right");

            writeFully(right.getSinkChannel(), "right-to-left");
            assertThat(readFully(left.getSourceChannel(), "right-to-left".length()))
                    .isEqualTo("right-to-left");
        } finally {
            closeQuietly(left);
            closeQuietly(right);
            closeWorker(worker);
        }
    }

    @Test
    void udpChannelsSendAndReceiveDatagrams() throws Exception {
        XnioWorker worker = createWorker();
        MulticastMessageChannel sender = null;
        MulticastMessageChannel receiver = null;
        try {
            InetAddress loopback = InetAddress.getLoopbackAddress();
            sender = worker.createUdpServer(new InetSocketAddress(loopback, 0), OptionMap.EMPTY);
            receiver = worker.createUdpServer(
                    new InetSocketAddress(loopback, 0),
                    OptionMap.builder().set(Options.REUSE_ADDRESSES, true).getMap());

            InetSocketAddress receiverAddress = receiver.getLocalAddress(InetSocketAddress.class);
            InetSocketAddress senderAddress = sender.getLocalAddress(InetSocketAddress.class);
            assertThat(receiverAddress.getPort()).isPositive();
            assertThat(senderAddress.getPort()).isPositive();

            ByteBuffer payload = StandardCharsets.UTF_8.encode("udp-datagram");
            sendDatagram(sender, receiverAddress, payload);

            SocketAddressBuffer addresses = new SocketAddressBuffer();
            ByteBuffer received = ByteBuffer.allocate("udp-datagram".length());
            int bytes = receiveDatagram(receiver, addresses, received);
            received.flip();

            assertThat(bytes).isEqualTo("udp-datagram".length());
            assertThat(StandardCharsets.UTF_8.decode(received).toString()).isEqualTo("udp-datagram");
            assertThat(addresses.getSourceAddress()).isInstanceOf(InetSocketAddress.class);
            assertThat(((InetSocketAddress) addresses.getSourceAddress()).getPort())
                    .isEqualTo(senderAddress.getPort());
        } finally {
            closeQuietly(sender);
            closeQuietly(receiver);
            closeWorker(worker);
        }
    }

    private static XnioWorker createWorker() throws IOException {
        OptionMap workerOptions = OptionMap.builder()
                .set(Options.WORKER_NAME, "xnio-nio-test")
                .set(Options.WORKER_IO_THREADS, 1)
                .set(Options.WORKER_TASK_CORE_THREADS, 1)
                .set(Options.WORKER_TASK_MAX_THREADS, 1)
                .set(Options.THREAD_DAEMON, true)
                .getMap();
        return Xnio.getInstance("nio").createWorker(workerOptions);
    }

    private static void acceptFirstConnection(
            AcceptingChannel<StreamConnection> server,
            AtomicReference<StreamConnection> acceptedReference,
            AtomicReference<IOException> acceptFailure,
            CountDownLatch acceptedLatch) {
        try {
            StreamConnection connection = server.accept();
            if (connection != null && acceptedReference.compareAndSet(null, connection)) {
                acceptedLatch.countDown();
            } else {
                closeQuietly(connection);
            }
        } catch (IOException e) {
            acceptFailure.set(e);
            acceptedLatch.countDown();
        }
    }

    private static StreamConnection awaitAcceptedConnection(
            CountDownLatch acceptedLatch,
            AtomicReference<StreamConnection> acceptedReference,
            AtomicReference<IOException> acceptFailure) throws IOException, InterruptedException {
        assertThat(acceptedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        if (acceptFailure.get() != null) {
            throw acceptFailure.get();
        }
        assertThat(acceptedReference.get()).isNotNull();
        return acceptedReference.get();
    }

    private static <T> T getFutureResult(IoFuture<T> future)
            throws IOException, InterruptedException, CancellationException {
        IoFuture.Status status = future.awaitInterruptibly(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (status == IoFuture.Status.FAILED) {
            throw future.getException();
        }
        assertThat(status).isEqualTo(IoFuture.Status.DONE);
        return future.getInterruptibly();
    }

    private static void writeFully(StreamSinkChannel channel, String value) throws IOException {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(value);
        long deadline = deadlineNanos();
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer);
            if (written == 0) {
                ensureTimeRemaining(deadline, "writing stream data");
                channel.awaitWritable(1, TimeUnit.SECONDS);
            }
        }
        while (!channel.flush()) {
            ensureTimeRemaining(deadline, "flushing stream data");
            channel.awaitWritable(1, TimeUnit.SECONDS);
        }
    }

    private static String readFully(StreamSourceChannel channel, int expectedBytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(expectedBytes);
        long deadline = deadlineNanos();
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read == -1) {
                throw new IOException("Stream ended before the expected bytes were read");
            }
            if (read == 0) {
                ensureTimeRemaining(deadline, "reading stream data");
                channel.awaitReadable(1, TimeUnit.SECONDS);
            }
        }
        buffer.flip();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    private static void sendDatagram(
            MulticastMessageChannel channel,
            SocketAddress target,
            ByteBuffer payload) throws IOException {
        long deadline = deadlineNanos();
        while (payload.hasRemaining()) {
            if (channel.sendTo(target, payload)) {
                return;
            }
            ensureTimeRemaining(deadline, "sending a UDP datagram");
            channel.awaitWritable(1, TimeUnit.SECONDS);
        }
    }

    private static int receiveDatagram(
            MulticastMessageChannel channel,
            SocketAddressBuffer addresses,
            ByteBuffer target) throws IOException {
        long deadline = deadlineNanos();
        int received = channel.receiveFrom(addresses, target);
        while (received == 0) {
            ensureTimeRemaining(deadline, "receiving a UDP datagram");
            channel.awaitReadable(1, TimeUnit.SECONDS);
            received = channel.receiveFrom(addresses, target);
        }
        return received;
    }

    private static long deadlineNanos() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    }

    private static void ensureTimeRemaining(long deadlineNanos, String operation) {
        if (System.nanoTime() >= deadlineNanos) {
            throw new AssertionError("Timed out while " + operation);
        }
    }

    private static void closeWorker(XnioWorker worker) throws InterruptedException {
        worker.shutdownNow();
        assertThat(worker.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Best-effort cleanup after assertions or I/O failures.
        }
    }
}
