/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_xnio.xnio_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManager;

import org.junit.jupiter.api.Test;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ByteBufferSlicePool;
import org.xnio.ByteString;
import org.xnio.ChannelListeners;
import org.xnio.FileAccess;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.LocalSocketAddress;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Pooled;
import org.xnio.Property;
import org.xnio.Sequence;
import org.xnio.SslClientAuthMode;
import org.xnio.streams.Streams;

public class Xnio_apiTest {
    @Test
    void optionsCanBeResolvedParsedAndStoredInOptionMaps() {
        ClassLoader classLoader = Xnio_apiTest.class.getClassLoader();
        Option<?> resolvedOption = Option.fromString("org.xnio.Options.WORKER_IO_THREADS", classLoader);

        OptionMap optionMap = OptionMap.builder()
                .parse(Options.WORKER_IO_THREADS, "3", classLoader)
                .parse(Options.ALLOW_BLOCKING, "true", classLoader)
                .parse(Options.FILE_ACCESS, "READ_WRITE", classLoader)
                .parse(Options.SSL_CLIENT_AUTH_MODE, "REQUESTED", classLoader)
                .set(Options.WORKER_NAME, "xnio-test-worker")
                .setSequence(Options.SSL_ENABLED_PROTOCOLS, "TLSv1.3", "TLSv1.2")
                .set(Options.SSL_JSSE_KEY_MANAGER_CLASSES,
                        Sequence.of(List.<Class<? extends KeyManager>>of(KeyManager.class)))
                .getMap();

        assertThat(resolvedOption).isSameAs(Options.WORKER_IO_THREADS);
        assertThat(optionMap.get(Options.WORKER_IO_THREADS, 0)).isEqualTo(3);
        assertThat(optionMap.get(Options.ALLOW_BLOCKING, false)).isTrue();
        assertThat(optionMap.get(Options.FILE_ACCESS)).isEqualTo(FileAccess.READ_WRITE);
        assertThat(optionMap.get(Options.SSL_CLIENT_AUTH_MODE)).isEqualTo(SslClientAuthMode.REQUESTED);
        assertThat(optionMap.get(Options.WORKER_NAME)).isEqualTo("xnio-test-worker");
        assertThat(optionMap.get(Options.SSL_ENABLED_PROTOCOLS))
                .containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(optionMap.get(Options.SSL_JSSE_KEY_MANAGER_CLASSES))
                .containsExactly(KeyManager.class);
        assertThat(optionMap).contains(Options.WORKER_IO_THREADS, Options.SSL_ENABLED_PROTOCOLS);
        assertThat(optionMap).isEqualTo(OptionMap.builder().addAll(optionMap).getMap());
    }

    @Test
    void sequencesPropertiesAndOptionSetsPreserveTypedValues() {
        Sequence<Property> saslProperties = Sequence.of(
                Property.of("realm", "ApplicationRealm"),
                Property.of("trace", Boolean.TRUE));
        OptionMap optionMap = OptionMap.create(Options.SASL_PROPERTIES, saslProperties);
        Set<Option<?>> options = Option.setBuilder()
                .add(Options.SASL_PROPERTIES, Options.TCP_NODELAY)
                .add(Options.REUSE_ADDRESSES)
                .create();

        assertThat(optionMap.get(Options.SASL_PROPERTIES)).containsExactlyElementsOf(saslProperties);
        assertThat(saslProperties.cast(Property.class)).containsExactlyElementsOf(saslProperties);
        assertThat(Property.of("realm", "ApplicationRealm").getKey()).isEqualTo("realm");
        assertThat(Property.of("realm", "ApplicationRealm").getValue()).isEqualTo("ApplicationRealm");
        assertThat(options).containsExactlyInAnyOrder(
                Options.SASL_PROPERTIES,
                Options.TCP_NODELAY,
                Options.REUSE_ADDRESSES);
    }

    @Test
    void byteStringSupportsAsciiSearchingCaseInsensitiveMatchingAndNumericConversion() throws IOException {
        ByteString value = ByteString.getBytes("Hello-XNIO-123", StandardCharsets.UTF_8);
        ByteString suffix = value.substring(6);
        ByteBuffer target = ByteBuffer.allocate(16);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        suffix.appendTo(target);
        target.flip();
        value.writeTo(output);

        assertThat(value.length()).isEqualTo(14);
        assertThat(suffix.toUtf8String()).isEqualTo("XNIO-123");
        assertThat(value.contains("XNIO")).isTrue();
        assertThat(value.containsIgnoreCase("xnio")).isTrue();
        assertThat(value.startsWith("Hello")).isTrue();
        assertThat(value.endsWith('3')).isTrue();
        assertThat(value.regionMatches(false, 6, "XNIO", 0, 4)).isTrue();
        assertThat(ByteString.fromInt(12345).toInt()).isEqualTo(12345);
        assertThat(ByteString.fromLong(987654321L).toLong()).isEqualTo(987654321L);
        assertThat(new String(Buffers.take(target), StandardCharsets.UTF_8)).isEqualTo("XNIO-123");
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("Hello-XNIO-123");
    }

    @Test
    void bufferUtilitiesCopyEncodeAndDecodeData() throws IOException {
        ByteBuffer source = ByteBuffer.wrap("abcdef".getBytes(StandardCharsets.UTF_8));
        ByteBuffer destination = ByteBuffer.allocate(8);
        ByteBuffer encoded = ByteBuffer.allocate(32);

        int copied = Buffers.copy(3, destination, source);
        Buffers.putModifiedUtf8(encoded, "A\u0000\u20ac");
        encoded.flip();
        destination.flip();

        assertThat(copied).isEqualTo(3);
        assertThat(new String(Buffers.take(destination), StandardCharsets.UTF_8)).isEqualTo("abc");
        assertThat(source.position()).isEqualTo(3);
        assertThat(Buffers.getModifiedUtf8(encoded)).isEqualTo("A\u0000\u20ac");

        ByteBuffer filled = ByteBuffer.allocate(4);
        Buffers.fill(filled, 'Z', 4).flip();
        assertThat(Buffers.take(filled)).containsExactly((byte) 'Z', (byte) 'Z', (byte) 'Z', (byte) 'Z');
    }

    @Test
    void byteBufferSlicePoolsAllocateAndReleasePooledBuffers() {
        ByteBufferSlicePool pool = new ByteBufferSlicePool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 16, 64);

        Pooled<ByteBuffer> firstAllocation = pool.allocate();
        ByteBuffer firstBuffer = firstAllocation.getResource();
        firstBuffer.put("pooled".getBytes(StandardCharsets.UTF_8));
        firstBuffer.flip();

        assertThat(pool.getBufferSize()).isEqualTo(16);
        assertThat(firstBuffer.capacity()).isEqualTo(pool.getBufferSize());
        assertThat(new String(Buffers.take(firstBuffer), StandardCharsets.UTF_8)).isEqualTo("pooled");

        firstAllocation.close();

        Pooled<ByteBuffer> secondAllocation = pool.allocate();
        try {
            ByteBuffer secondBuffer = secondAllocation.getResource();

            assertThat(secondBuffer.position()).isZero();
            assertThat(secondBuffer.limit()).isEqualTo(pool.getBufferSize());
            assertThat(secondBuffer.capacity()).isEqualTo(pool.getBufferSize());
        } finally {
            secondAllocation.close();
            pool.clean();
        }
    }

    @Test
    void futureResultsNotifySuccessFailureAndCancellation() throws Exception {
        FutureResult<String> successfulResult = new FutureResult<>(IoUtils.directExecutor());
        AtomicReference<String> notification = new AtomicReference<>();
        IoFuture<String> successfulFuture = successfulResult.getIoFuture();
        successfulFuture.addNotifier((future, attachment) -> attachment.set(future.getStatus().name()), notification);

        assertThat(successfulResult.setResult("ready")).isTrue();
        assertThat(successfulFuture.await(1, TimeUnit.SECONDS)).isEqualTo(IoFuture.Status.DONE);
        assertThat(successfulFuture.get()).isEqualTo("ready");
        assertThat(notification).hasValue("DONE");
        Future<String> javaFuture = IoUtils.getFuture(successfulFuture);
        assertThat(javaFuture.get(1, TimeUnit.SECONDS)).isEqualTo("ready");

        IOException failure = new IOException("expected failure");
        FutureResult<String> failedResult = new FutureResult<>();
        assertThat(failedResult.setException(failure)).isTrue();
        IoFuture<String> failedFuture = failedResult.getIoFuture();
        assertThat(failedFuture.await(1, TimeUnit.SECONDS)).isEqualTo(IoFuture.Status.FAILED);
        assertThat(failedFuture.getException()).isSameAs(failure);
        assertThatThrownBy(failedFuture::get).isSameAs(failure);

        FutureResult<String> cancelledResult = new FutureResult<>();
        AtomicBoolean cancelHandlerInvoked = new AtomicBoolean();
        cancelledResult.addCancelHandler(() -> {
            cancelHandlerInvoked.set(true);
            return IoUtils.nullCancellable();
        });
        cancelledResult.getIoFuture().cancel();
        assertThat(cancelHandlerInvoked).isTrue();
        assertThat(cancelledResult.setCancelled()).isTrue();
        assertThat(cancelledResult.getIoFuture().await(1, TimeUnit.SECONDS)).isEqualTo(IoFuture.Status.CANCELLED);
        assertThatThrownBy(() -> cancelledResult.getIoFuture().get()).isInstanceOf(CancellationException.class);
    }

    @Test
    void streamAndChannelHelpersCloseAndTransferData() throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream("stream-copy".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Streams.copyStream(input, output, true, 4);

        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("stream-copy");

        RecordingChannel channel = new RecordingChannel();
        RecordingCloseable attachment = new RecordingCloseable();
        AtomicReference<RecordingChannel> invoked = new AtomicReference<>();

        assertThat(ChannelListeners.invokeChannelListener(channel, invoked::set)).isTrue();
        ChannelListeners.closingChannelListener(attachment).handleEvent(channel);
        ChannelListeners.closingChannelListener().handleEvent(channel);
        IoUtils.safeClose((Closeable) () -> {
            throw new IOException("ignored");
        });

        assertThat(invoked).hasValue(channel);
        assertThat(channel.isOpen()).isFalse();
        assertThat(attachment.closed).isTrue();
        assertThat(IoUtils.nullCloseable()).isNotNull();
    }

    @Test
    void localSocketAddressHasStableNameAndDisplayForm() {
        LocalSocketAddress address = new LocalSocketAddress("xnio-local-test");

        assertThat(address.getName()).isEqualTo("xnio-local-test");
        assertThat(address.toString()).contains("xnio-local-test");
    }

    private static final class RecordingChannel implements Channel {
        private boolean open = true;

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    private static final class RecordingCloseable implements Closeable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
