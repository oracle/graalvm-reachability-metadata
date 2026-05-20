/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_xnio.xnio_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.xnio.BufferAllocator;
import org.xnio.ByteBufferPool;
import org.xnio.ByteString;
import org.xnio.FailedIoFuture;
import org.xnio.FileAccess;
import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Property;
import org.xnio.Sequence;
import org.xnio.sasl.SaslQop;
import org.xnio.sasl.SaslStrength;
import org.xnio.streams.LimitedInputStream;
import org.xnio.streams.LimitedOutputStream;
import org.xnio.streams.WriterOutputStream;

public class Xnio_apiTest {
    public static final Option<Class<? extends Number>> NUMBER_TYPE =
            Option.type(Xnio_apiTest.class, "NUMBER_TYPE", Number.class);

    @Test
    void optionsCanBeParsedCastedAndStoredInImmutableMaps() {
        Sequence<SaslQop> qopSequence = Options.SASL_QOP.parseValue("AUTH,AUTH_INT", null);
        Sequence<Property> saslProperties = Options.SASL_PROPERTIES.parseValue("realm=example.org,trace=true", null);

        OptionMap optionMap = OptionMap.builder()
                .parse(Options.TCP_NODELAY, "true")
                .parse(Options.WORKER_IO_THREADS, "3")
                .set(Options.SASL_STRENGTH, SaslStrength.HIGH)
                .set(Options.SASL_QOP, qopSequence)
                .set(Options.SASL_PROPERTIES, saslProperties)
                .set(Options.WORKER_NAME, "metadata-worker")
                .set(NUMBER_TYPE, Integer.class)
                .getMap();

        assertThat(Options.TCP_NODELAY.cast(Boolean.TRUE)).isTrue();
        assertThat(optionMap.get(Options.TCP_NODELAY, false)).isTrue();
        assertThat(optionMap.get(Options.WORKER_IO_THREADS, 0)).isEqualTo(3);
        assertThat(optionMap.get(Options.WORKER_NAME)).isEqualTo("metadata-worker");
        assertThat(optionMap.get(Options.SASL_STRENGTH)).isEqualTo(SaslStrength.HIGH);
        assertThat(optionMap.get(Options.SASL_QOP)).containsExactly(SaslQop.AUTH, SaslQop.AUTH_INT);
        assertThat(optionMap.get(NUMBER_TYPE)).isSameAs(Integer.class);
        assertThat(saslProperties).containsExactly(Property.of("realm", "example.org"), Property.of("trace", "true"));

        OptionMap fileOptions = OptionMap.create(Options.FILE_ACCESS, FileAccess.READ_ONLY);
        assertThat(fileOptions.get(Options.FILE_ACCESS)).isEqualTo(FileAccess.READ_ONLY);
        assertThat(optionMap).contains(Options.TCP_NODELAY, Options.SASL_QOP, NUMBER_TYPE);
    }

    @Test
    void bufferAllocatorsCreateHeapAndDirectBuffers() {
        ByteBuffer heapBuffer = BufferAllocator.BYTE_BUFFER_ALLOCATOR.allocate(8);
        heapBuffer.put("xnio".getBytes(StandardCharsets.US_ASCII));
        heapBuffer.flip();

        byte[] bytes = new byte[heapBuffer.remaining()];
        heapBuffer.get(bytes);
        assertThat(bytes).containsExactly((byte) 'x', (byte) 'n', (byte) 'i', (byte) 'o');

        ByteBuffer directBuffer = BufferAllocator.DIRECT_BYTE_BUFFER_ALLOCATOR.allocate(4);
        assertThat(directBuffer.isDirect()).isTrue();
        assertThat(directBuffer.capacity()).isEqualTo(4);
    }

    @Test
    void byteBufferPoolsAllocateBulkBuffersAndUseScopedCaches() {
        ByteBufferPool pool = ByteBufferPool.Set.HEAP.getSmall();
        assertThat(pool.isDirect()).isFalse();
        assertThat(pool.getSize()).isEqualTo(ByteBufferPool.SMALL_SIZE);

        ByteBuffer[] buffers = new ByteBuffer[3];
        pool.allocate(buffers, 1, 2);
        assertThat(buffers[0]).isNull();
        assertThat(buffers[1].capacity()).isEqualTo(ByteBufferPool.SMALL_SIZE);
        assertThat(buffers[2].capacity()).isEqualTo(ByteBufferPool.SMALL_SIZE);
        assertThat(buffers[1].isDirect()).isFalse();

        buffers[1].put(0, (byte) 0x7f);
        ByteBufferPool.zeroAndFree(buffers[1]);
        assertThat(buffers[1].position()).isZero();
        assertThat(buffers[1].limit()).isEqualTo(ByteBufferPool.SMALL_SIZE);
        assertThat(buffers[1].get(0)).isZero();

        ByteBufferPool.free(buffers, 2, 1);
        assertThat(buffers[2]).isNull();

        AtomicReference<ByteBuffer> firstAllocation = new AtomicReference<>();
        AtomicReference<ByteBuffer> cachedAllocation = new AtomicReference<>();
        pool.runWithCache(1, () -> {
            ByteBuffer buffer = pool.allocate();
            firstAllocation.set(buffer);
            ByteBufferPool.free(buffer);
            cachedAllocation.set(pool.allocate());
        });

        assertThat(cachedAllocation.get()).isSameAs(firstAllocation.get());
        ByteBufferPool.free(cachedAllocation.get());
        pool.flushCaches();
        ByteBufferPool.flushAllCaches();
    }

    @Test
    void byteStringsSupportImmutableAsciiSearchAndNumericConversion() throws Exception {
        byte[] bytes = "Content-Length: 42".getBytes(StandardCharsets.ISO_8859_1);
        ByteString header = ByteString.copyOf(bytes, 0, bytes.length);
        bytes[0] = 'x';

        assertThat(header.toString()).isEqualTo("Content-Length: 42");
        assertThat(header.startsWithIgnoreCase(ByteString.getBytes("content"))).isTrue();
        assertThat(header.containsIgnoreCase(ByteString.getBytes("length"))).isTrue();
        assertThat(header.indexOf(':')).isEqualTo("Content-Length".length());
        assertThat(header.toInt("Content-Length: ".length())).isEqualTo(42);
        assertThat(header.substring(0, "Content".length()).toString()).isEqualTo("Content");

        ByteBuffer target = ByteBuffer.allocate(8);
        int appended = header.tryAppendTo(0, target);
        assertThat(appended).isEqualTo(8);
        target.flip();
        assertThat(ByteString.getBytes(target).toString()).isEqualTo("Content-");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteString statusLinePrefix = ByteString.concat("HTTP/", ByteString.fromInt(2));
        statusLinePrefix.concat(".0").writeTo(output);
        assertThat(output.toString(StandardCharsets.ISO_8859_1)).isEqualTo("HTTP/2.0");
    }

    @Test
    void ioFuturesNotifyCompletionFailureAndCancellation() throws Exception {
        FinishedIoFuture<String> finished = new FinishedIoFuture<>("ready");
        AtomicReference<String> finishedAttachment = new AtomicReference<>("notified");
        finished.addNotifier(
                (future, attachment) -> attachment.set(future.getStatus() + ":" + attachment.get()),
                finishedAttachment);
        assertThat(finished.get()).isEqualTo("ready");
        assertThat(finished.getStatus()).isEqualTo(IoFuture.Status.DONE);
        assertThat(finishedAttachment).hasValue("DONE:notified");

        IOException failure = new IOException("boom");
        FailedIoFuture<String> failed = new FailedIoFuture<>(failure);
        assertThat(failed.await(10, TimeUnit.MILLISECONDS)).isEqualTo(IoFuture.Status.FAILED);
        assertThat(failed.getException()).isSameAs(failure);

        FutureResult<String> result = new FutureResult<>();
        IoFuture<String> future = result.getIoFuture();
        AtomicBoolean cancelHandlerCalled = new AtomicBoolean();
        AtomicReference<IoFuture.Status> observedStatus = new AtomicReference<>();
        result.addCancelHandler(() -> {
            cancelHandlerCalled.set(true);
            return null;
        });
        future.addNotifier((notifiedFuture, attachment) -> attachment.set(notifiedFuture.getStatus()), observedStatus);

        assertThat(future.await(1, TimeUnit.MILLISECONDS)).isEqualTo(IoFuture.Status.WAITING);
        assertThat(result.setResult("complete")).isTrue();
        assertThat(future.awaitInterruptibly(10, TimeUnit.MILLISECONDS)).isEqualTo(IoFuture.Status.DONE);
        assertThat(future.getInterruptibly()).isEqualTo("complete");
        assertThat(observedStatus).hasValue(IoFuture.Status.DONE);
        assertThat(result.setCancelled()).isFalse();
        assertThat(cancelHandlerCalled).isFalse();
    }

    @Test
    void streamAdaptersEnforceLimitsAndDecodeBytes() throws Exception {
        ByteArrayInputStream bytes = new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8));
        LimitedInputStream input = new LimitedInputStream(bytes, 4);
        assertThat(input.read()).isEqualTo('a');
        input.mark(10);
        assertThat(input.read(new byte[2])).isEqualTo(2);
        input.reset();
        assertThat(input.readAllBytes()).containsExactly((byte) 'b', (byte) 'c', (byte) 'd');
        assertThat(input.read()).isEqualTo(-1);

        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        LimitedOutputStream output = new LimitedOutputStream(outputBytes, 5);
        output.write("xnio".getBytes(StandardCharsets.UTF_8));
        output.write('!');
        assertThrows(IOException.class, () -> output.write('?'));
        output.close();
        assertThat(outputBytes.toString(StandardCharsets.UTF_8)).isEqualTo("xnio!");

        StringWriter decoded = new StringWriter();
        try (WriterOutputStream writerOutputStream = new WriterOutputStream(
                decoded,
                StandardCharsets.UTF_8.newDecoder(),
                4)) {
            writerOutputStream.write("Grüße".getBytes(StandardCharsets.UTF_8));
        }
        assertThat(decoded).hasToString("Grüße");
    }

}
