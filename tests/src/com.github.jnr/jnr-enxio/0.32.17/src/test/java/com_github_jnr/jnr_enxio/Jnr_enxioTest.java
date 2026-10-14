/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jnr_enxio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Set;
import jnr.constants.platform.AddressFamily;
import jnr.constants.platform.Sock;
import jnr.enxio.channels.Native;
import jnr.enxio.channels.NativeDeviceChannel;
import jnr.enxio.channels.NativeException;
import jnr.enxio.channels.NativeFileSelectorProvider;
import jnr.enxio.channels.NativeSelectorProvider;
import jnr.enxio.channels.NativeServerSocketChannel;
import jnr.enxio.channels.NativeSocketChannel;
import jnr.ffi.LibraryLoader;
import jnr.ffi.Platform;
import jnr.ffi.annotations.Out;
import org.junit.jupiter.api.Test;

public class Jnr_enxioTest {
    private static final NativeLibrary LIBC = LibraryLoader.create(NativeLibrary.class)
            .load(Platform.getNativePlatform().getStandardCLibraryName());

    @Test
    void transfersBytesThroughNativeFunctions() throws Exception {
        int[] descriptors = openPipe();
        try {
            ByteBuffer source = StandardCharsets.UTF_8.encode("native-pipe-payload");
            int expectedLength = source.remaining();

            assertThat(Native.write(descriptors[1], source)).isEqualTo(expectedLength);
            assertThat(source.hasRemaining()).isFalse();

            ByteBuffer destination = ByteBuffer.allocate(expectedLength);
            assertThat(Native.read(descriptors[0], destination)).isEqualTo(expectedLength);
            assertThat(destination.position()).isEqualTo(expectedLength);
            destination.flip();
            assertThat(StandardCharsets.UTF_8.decode(destination).toString()).isEqualTo("native-pipe-payload");
        } finally {
            closeDescriptors(descriptors);
        }
    }

    @Test
    void reportsNativeErrorsAndRejectsInvalidBuffers() {
        assertThatThrownBy(() -> Native.close(-1))
                .isInstanceOf(NativeException.class)
                .satisfies(error -> {
                    NativeException nativeError = (NativeException) error;
                    assertThat(nativeError.getErrno()).isNotNull();
                    assertThat(nativeError.getMessage()).contains("Error closing fd -1");
                });
        assertThatThrownBy(() -> Native.read(-1, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Native.read(-1, ByteBuffer.allocate(1).asReadOnlyBuffer()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Read-only buffer");
        assertThatThrownBy(() -> Native.write(-1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void transfersBytesThroughDeviceChannels() throws Exception {
        int[] descriptors = openPipe();
        try (NativeDeviceChannel reader = new NativeDeviceChannel(descriptors[0]);
                NativeDeviceChannel writer = new NativeDeviceChannel(descriptors[1])) {
            assertThat(reader.getFD()).isEqualTo(descriptors[0]);
            assertThat(reader.validOps()).isEqualTo(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            reader.configureBlocking(false);
            assertThat(reader.isBlocking()).isFalse();
            assertThat(reader.read(ByteBuffer.allocate(1))).isZero();

            byte[] payload = "device-channel".getBytes(StandardCharsets.UTF_8);
            ByteBuffer source = ByteBuffer.wrap(payload);
            assertThat(writer.write(source)).isEqualTo(payload.length);

            ByteBuffer destination = ByteBuffer.allocate(payload.length);
            assertThat(reader.read(destination)).isEqualTo(payload.length);
            assertThat(destination.array()).containsExactly(payload);
        }
    }

    @Test
    void selectsReadableDeviceChannelsWithNativeProvider() throws Exception {
        assertReadableSelection(NativeSelectorProvider.getInstance(), "native-selector");
    }

    @Test
    void selectsReadableDeviceChannelsWithFileProvider() throws Exception {
        SelectorProvider provider = NativeFileSelectorProvider.getInstance();
        assertThat(NativeFileSelectorProvider.getInstance()).isSameAs(provider);
        assertReadableSelection(provider, "file-selector");
    }

    @Test
    void wakesAWaitingSelector() throws Exception {
        try (Selector selector = NativeSelectorProvider.getInstance().openSelector()) {
            assertThat(selector.wakeup()).isSameAs(selector);
            assertThat(selector.select(10_000)).isZero();
            assertThat(selector.isOpen()).isTrue();
        }
    }

    @Test
    void updatesSelectionInterestFromWritableToReadable() throws Exception {
        int[] descriptors = openSocketPair();
        try (NativeSocketChannel selectedChannel = new NativeSocketChannel(descriptors[0]);
                NativeSocketChannel peerChannel = new NativeSocketChannel(descriptors[1]);
                Selector selector = NativeSelectorProvider.getInstance().openSelector()) {
            selectedChannel.configureBlocking(false);
            SelectionKey key = selectedChannel.register(selector, SelectionKey.OP_WRITE);

            assertThat(selector.select(10_000)).isOne();
            assertThat(key.isWritable()).isTrue();
            selector.selectedKeys().clear();

            assertThat(key.interestOps(SelectionKey.OP_READ)).isSameAs(key);
            assertThat(key.interestOps()).isEqualTo(SelectionKey.OP_READ);
            assertThat(peerChannel.write(ByteBuffer.wrap(new byte[] {7}))).isOne();

            assertThat(selector.select(10_000)).isOne();
            assertThat(key.isReadable()).isTrue();
            assertThat(key.isWritable()).isFalse();
        }
    }

    @Test
    void transfersAndShutsDownSocketPair() throws Exception {
        int[] descriptors = openSocketPair();
        try (NativeSocketChannel left = new NativeSocketChannel(descriptors[0]);
                NativeSocketChannel right = new NativeSocketChannel(descriptors[1])) {
            assertThat(left.getFD()).isEqualTo(descriptors[0]);
            assertThat(left.validOps()).isEqualTo(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            left.configureBlocking(false);
            assertThat(left.read(ByteBuffer.allocate(1))).isZero();
            left.configureBlocking(true);

            byte[] payload = "socket-channel".getBytes(StandardCharsets.UTF_8);
            assertThat(left.write(ByteBuffer.wrap(payload))).isEqualTo(payload.length);
            ByteBuffer destination = ByteBuffer.allocate(payload.length);
            assertThat(right.read(destination)).isEqualTo(payload.length);
            assertThat(destination.array()).containsExactly(payload);

            left.shutdownOutput();
            assertThat(right.read(ByteBuffer.allocate(1))).isEqualTo(-1);
            left.shutdownInput();
        }
    }

    @Test
    void exposesServerChannelDescriptorAndOperations() throws Exception {
        int[] descriptors = openSocketPair();
        try (NativeServerSocketChannel channel = new NativeServerSocketChannel(descriptors[0])) {
            assertThat(channel.getFD()).isEqualTo(descriptors[0]);
            assertThat(channel.validOps()).isEqualTo(SelectionKey.OP_ACCEPT | SelectionKey.OP_READ);
            assertThat(channel.provider()).isSameAs(NativeSelectorProvider.getInstance());

            channel.configureBlocking(false);
            assertThat(channel.isBlocking()).isFalse();
            channel.configureBlocking(true);
            assertThat(channel.isBlocking()).isTrue();
        } finally {
            Native.close(descriptors[1]);
        }
    }

    private static void assertReadableSelection(SelectorProvider provider, String attachment) throws Exception {
        int[] descriptors = openPipe();
        try (NativeDeviceChannel reader =
                        new NativeDeviceChannel(provider, descriptors[0], SelectionKey.OP_READ, false);
                NativeDeviceChannel writer =
                        new NativeDeviceChannel(provider, descriptors[1], SelectionKey.OP_WRITE, false);
                Selector selector = provider.openSelector()) {
            reader.configureBlocking(false);
            SelectionKey key = reader.register(selector, SelectionKey.OP_READ, attachment);
            assertThat(selector.keys()).containsExactly(key);

            assertThat(writer.write(ByteBuffer.wrap(new byte[] {42}))).isOne();
            assertThat(selector.select(10_000)).isOne();

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            assertThat(selectedKeys).containsExactly(key);
            assertThat(key.isReadable()).isTrue();
            assertThat(key.attachment()).isEqualTo(attachment);
            assertThat(key.channel()).isSameAs(reader);
            assertThat(key.selector()).isSameAs(selector);

            ByteBuffer destination = ByteBuffer.allocate(1);
            assertThat(reader.read(destination)).isOne();
            assertThat(destination.get(0)).isEqualTo((byte) 42);

            selectedKeys.clear();
            key.cancel();
            assertThat(selector.selectNow()).isZero();
            assertThat(selector.keys()).isEmpty();
        }
    }

    private static int[] openPipe() {
        int[] descriptors = new int[2];
        assertThat(LIBC.pipe(descriptors)).isZero();
        assertThat(descriptors[0]).isGreaterThanOrEqualTo(0);
        assertThat(descriptors[1]).isGreaterThanOrEqualTo(0);
        return descriptors;
    }

    private static int[] openSocketPair() {
        int[] descriptors = new int[2];
        int result = LIBC.socketpair(
                AddressFamily.AF_UNIX.intValue(), Sock.SOCK_STREAM.intValue(), 0, descriptors);
        assertThat(result).isZero();
        assertThat(descriptors[0]).isGreaterThanOrEqualTo(0);
        assertThat(descriptors[1]).isGreaterThanOrEqualTo(0);
        return descriptors;
    }

    private static void closeDescriptors(int[] descriptors) throws IOException {
        IOException failure = null;
        for (int descriptor : descriptors) {
            try {
                Native.close(descriptor);
            } catch (IOException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public interface NativeLibrary {
        int pipe(@Out int[] descriptors);

        int socketpair(int domain, int type, int protocol, @Out int[] descriptors);
    }
}
