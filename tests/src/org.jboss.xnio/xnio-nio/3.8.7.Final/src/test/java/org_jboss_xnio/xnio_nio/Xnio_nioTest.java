/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_xnio.xnio_nio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xnio.ChannelPipe;
import org.xnio.FileAccess;
import org.xnio.FileChangeEvent;
import org.xnio.FileSystemWatcher;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.Channels;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.channels.SocketAddressBuffer;
import org.xnio.channels.StreamSinkChannel;
import org.xnio.channels.StreamSourceChannel;
import org.xnio.nio.NioXnioProvider;
import org.xnio.nio.Version;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class Xnio_nioTest {

    private static final AtomicInteger WORKER_IDS = new AtomicInteger();
    private static final long IO_TIMEOUT_SECONDS = 5L;

    @Test
    void providerIsDiscoveredAndExposesNioImplementation() {
        NioXnioProvider provider = new NioXnioProvider();

        assertThat(provider.getName()).isEqualTo("nio");
        assertThat(provider.getInstance().getName()).isEqualTo("nio");
        assertThat(Xnio.getInstance("nio").getName()).isEqualTo(provider.getInstance().getName());
        assertThat(Version.getJarName()).contains("xnio");
        assertThat(Version.getVersionString()).isNotBlank();
    }

    @Test
    void xnioOpensFilesWithConfiguredAccess(@TempDir Path tempDir) throws Exception {
        Xnio xnio = Xnio.getInstance("nio");
        Path file = tempDir.resolve("nio-file-channel.txt");
        OptionMap options = OptionMap.builder()
                .set(Options.FILE_ACCESS, FileAccess.READ_WRITE)
                .set(Options.FILE_CREATE, true)
                .getMap();

        try (FileChannel channel = xnio.openFile(file.toFile(), options)) {
            ByteBuffer payload = ByteBuffer.wrap("file-through-xnio".getBytes(StandardCharsets.UTF_8));
            while (payload.hasRemaining()) {
                assertThat(channel.write(payload)).isPositive();
            }
            channel.position(0L);
            ByteBuffer received = ByteBuffer.allocate("file-through-xnio".length());
            while (received.hasRemaining()) {
                assertThat(channel.read(received)).isPositive();
            }
            received.flip();
            assertThat(StandardCharsets.UTF_8.decode(received).toString()).isEqualTo("file-through-xnio");
        }

        assertThat(Files.readString(file)).isEqualTo("file-through-xnio");
    }

    @Test
    void tcpStreamServerAcceptsClientAndEchoesBytes() throws Exception {
        XnioWorker worker = createWorker();
        StreamConnection serverConnection = null;
        StreamConnection clientConnection = null;
        CountDownLatch accepted = new CountDownLatch(1);
        AtomicReference<StreamConnection> acceptedConnection = new AtomicReference<>();
        AtomicReference<IOException> acceptFailure = new AtomicReference<>();
        try (AcceptingChannel<StreamConnection> server = worker.createStreamConnectionServer(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), channel -> {
                    try {
                        acceptedConnection.set(channel.accept());
                    } catch (IOException e) {
                        acceptFailure.set(e);
                    } finally {
                        accepted.countDown();
                    }
                }, socketOptions())) {
            server.resumeAccepts();
            InetSocketAddress address = server.getLocalAddress(InetSocketAddress.class);
            IoFuture<StreamConnection> clientFuture = worker.openStreamConnection(
                    address, ignored -> { }, socketOptions());

            assertThat(accepted.await(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(acceptFailure.get()).isNull();
            serverConnection = acceptedConnection.get();
            assertThat(serverConnection).isNotNull();
            assertThat(clientFuture.awaitInterruptibly(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo(IoFuture.Status.DONE);
            clientConnection = clientFuture.getInterruptibly();

            assertThat(clientConnection.getPeerAddress(InetSocketAddress.class).getPort()).isEqualTo(address.getPort());
            assertThat(serverConnection.getPeerAddress(InetSocketAddress.class)).isNotNull();

            writeText(clientConnection.getSinkChannel(), "ping over nio");
            assertThat(readText(serverConnection.getSourceChannel(), "ping over nio".length()))
                    .isEqualTo("ping over nio");

            writeText(serverConnection.getSinkChannel(), "pong over nio");
            assertThat(readText(clientConnection.getSourceChannel(), "pong over nio".length()))
                    .isEqualTo("pong over nio");
        } finally {
            IoUtils.safeClose(clientConnection);
            IoUtils.safeClose(serverConnection);
            shutdownWorker(worker);
        }
    }

    @Test
    void fullDuplexPipeConnectionsTransferDataInBothDirections() throws Exception {
        XnioWorker worker = createWorker();
        try {
            ChannelPipe<StreamConnection, StreamConnection> pipe = worker.createFullDuplexPipeConnection();
            try (StreamConnection left = pipe.getLeftSide(); StreamConnection right = pipe.getRightSide()) {
                writeText(left.getSinkChannel(), "left-to-right");
                assertThat(readText(right.getSourceChannel(), "left-to-right".length())).isEqualTo("left-to-right");

                writeText(right.getSinkChannel(), "right-to-left");
                assertThat(readText(left.getSourceChannel(), "right-to-left".length())).isEqualTo("right-to-left");
            }
        } finally {
            shutdownWorker(worker);
        }
    }

    @Test
    void udpServersExchangeDatagramsOnLoopback() throws Exception {
        XnioWorker worker = createWorker();
        try (MulticastMessageChannel sender = worker.createUdpServer(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), udpOptions());
                MulticastMessageChannel receiver = worker.createUdpServer(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), udpOptions())) {
            InetSocketAddress receiverAddress = receiver.getLocalAddress(InetSocketAddress.class);
            ByteBuffer payload = ByteBuffer.wrap("nio-datagram".getBytes(StandardCharsets.UTF_8));
            boolean sent = sender.sendTo(receiverAddress, payload);
            if (!sent) {
                sender.awaitWritable(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                sent = sender.sendTo(receiverAddress, payload);
            }
            assertThat(sent).isTrue();

            SocketAddressBuffer sourceAddress = new SocketAddressBuffer();
            ByteBuffer received = ByteBuffer.allocate("nio-datagram".length());
            int byteCount = 0;
            for (int attempts = 0; attempts < 3 && byteCount == 0; attempts++) {
                receiver.awaitReadable(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                byteCount = receiver.receiveFrom(sourceAddress, received);
            }

            assertThat(byteCount).isEqualTo("nio-datagram".length());
            assertThat(sourceAddress.getSourceAddress(InetSocketAddress.class)).isNotNull();
            received.flip();
            assertThat(StandardCharsets.UTF_8.decode(received).toString()).isEqualTo("nio-datagram");
        } finally {
            shutdownWorker(worker);
        }
    }

    @Test
    void fileSystemWatcherReportsCreatedFiles(@TempDir Path tempDir) throws Exception {
        Xnio xnio = Xnio.getInstance("nio");
        CountDownLatch observedChange = new CountDownLatch(1);
        AtomicReference<FileChangeEvent.Type> observedType = new AtomicReference<>();
        Path createdFile = tempDir.resolve("created-by-test.txt");

        try (FileSystemWatcher watcher = xnio.createFileSystemWatcher("xnio-nio-test-watcher", OptionMap.EMPTY)) {
            watcher.watchPath(tempDir.toFile(), events -> events.stream()
                    .filter(event -> event.getFile().toPath().getFileName().equals(createdFile.getFileName()))
                    .findFirst()
                    .ifPresent(event -> {
                        observedType.set(event.getType());
                        observedChange.countDown();
                    }));

            Files.writeString(createdFile, "watch me", StandardCharsets.UTF_8);

            assertThat(observedChange.await(10L, TimeUnit.SECONDS)).isTrue();
            assertThat(observedType.get()).isIn(FileChangeEvent.Type.ADDED, FileChangeEvent.Type.MODIFIED);
        }
    }

    private static XnioWorker createWorker() throws IOException {
        OptionMap workerOptions = OptionMap.builder()
                .set(Options.WORKER_NAME, "xnio-nio-test-" + WORKER_IDS.incrementAndGet())
                .set(Options.WORKER_IO_THREADS, 2)
                .set(Options.WORKER_TASK_CORE_THREADS, 1)
                .set(Options.WORKER_TASK_MAX_THREADS, 2)
                .set(Options.WORKER_TASK_KEEPALIVE, 1000)
                .getMap();
        return Xnio.getInstance("nio").createWorker(workerOptions);
    }

    private static OptionMap socketOptions() {
        return OptionMap.builder()
                .set(Options.REUSE_ADDRESSES, true)
                .set(Options.TCP_NODELAY, true)
                .set(Options.READ_TIMEOUT, (int) TimeUnit.SECONDS.toMillis(IO_TIMEOUT_SECONDS))
                .set(Options.WRITE_TIMEOUT, (int) TimeUnit.SECONDS.toMillis(IO_TIMEOUT_SECONDS))
                .getMap();
    }

    private static OptionMap udpOptions() {
        return OptionMap.builder()
                .set(Options.REUSE_ADDRESSES, true)
                .set(Options.BROADCAST, false)
                .set(Options.READ_TIMEOUT, (int) TimeUnit.SECONDS.toMillis(IO_TIMEOUT_SECONDS))
                .set(Options.WRITE_TIMEOUT, (int) TimeUnit.SECONDS.toMillis(IO_TIMEOUT_SECONDS))
                .getMap();
    }

    private static void writeText(StreamSinkChannel channel, String text) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) {
            assertThat(Channels.writeBlocking(channel, buffer, IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isPositive();
        }
        assertThat(Channels.flushBlocking(channel, IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private static String readText(StreamSourceChannel channel, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            assertThat(Channels.readBlocking(channel, buffer, IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isPositive();
        }
        buffer.flip();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    private static void shutdownWorker(XnioWorker worker) throws InterruptedException {
        worker.shutdown();
        if (!worker.awaitTermination(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }
}
