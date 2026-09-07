/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_buffer_netty;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.buffer.netty.ByteBufAllocatorConfiguration;
import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
public class Micronaut_buffer_nettyTest {
    private static final byte[] SAMPLE = "netty-buffer".getBytes(StandardCharsets.UTF_8);

    @Test
    void allocatesAndManipulatesNettyBackedBuffers() {
        NettyByteBufferFactory factory =
                new NettyByteBufferFactory(UnpooledByteBufAllocator.DEFAULT);
        ByteBuffer<ByteBuf> buffer = factory.buffer(8, 64);

        try {
            assertThat(factory.getNativeAllocator()).isSameAs(UnpooledByteBufAllocator.DEFAULT);
            assertThat(buffer.readerIndex()).isZero();
            assertThat(buffer.writerIndex()).isZero();
            assertThat(buffer.writableBytes()).isEqualTo(8);
            assertThat(buffer.maxCapacity()).isEqualTo(64);

            buffer.write((byte) 'A')
                    .write("BC", StandardCharsets.UTF_8)
                    .write(new byte[] {'x', 'D', 'E', 'y'}, 1, 2);

            assertThat(buffer.readableBytes()).isEqualTo(5);
            assertThat(buffer.writerIndex()).isEqualTo(5);
            assertThat(buffer.getByte(0)).isEqualTo((byte) 'A');
            assertThat(buffer.indexOf((byte) 'D')).isEqualTo(3);
            assertThat(buffer.toString(StandardCharsets.UTF_8)).isEqualTo("ABCDE");
            assertThat(StandardCharsets.UTF_8.decode(buffer.asNioBuffer()).toString())
                    .isEqualTo("ABCDE");
            assertThat(StandardCharsets.UTF_8.decode(buffer.asNioBuffer(1, 3)).toString())
                    .isEqualTo("BCD");
            assertThat(buffer.slice(1, 3).toByteArray())
                    .containsExactly("BCD".getBytes(StandardCharsets.UTF_8));

            assertThat(buffer.read()).isEqualTo((byte) 'A');
            assertThat(buffer.readCharSequence(2, StandardCharsets.UTF_8).toString())
                    .isEqualTo("BC");
            byte[] destination = new byte[4];
            buffer.read(destination, 1, 2);
            assertThat(destination).containsExactly((byte) 0, (byte) 'D', (byte) 'E', (byte) 0);
            assertThat(buffer.readableBytes()).isZero();

            buffer.readerIndex(0).writerIndex(4).capacity(16);
            assertThat(buffer.readerIndex()).isZero();
            assertThat(buffer.writerIndex()).isEqualTo(4);
            assertThat(buffer.writableBytes()).isEqualTo(12);
            assertThat(buffer.toByteArray())
                    .containsExactly("ABCD".getBytes(StandardCharsets.UTF_8));
        } finally {
            release(buffer.asNativeBuffer());
        }
    }

    @Test
    void createsCopiedWrappedAndReferenceCountedBuffers() {
        NettyByteBufferFactory factory =
                new NettyByteBufferFactory(UnpooledByteBufAllocator.DEFAULT);
        byte[] copiedSource = SAMPLE.clone();
        ByteBuffer<ByteBuf> copied = factory.copiedBuffer(copiedSource);
        ByteBuffer<ByteBuf> copiedNio =
                factory.copiedBuffer(StandardCharsets.UTF_8.encode("nio-copy"));
        byte[] wrappedSource = "wrapped".getBytes(StandardCharsets.UTF_8);
        ByteBuffer<ByteBuf> wrapped = factory.wrap(wrappedSource);
        ByteBuf nativeBuffer = Unpooled.copiedBuffer(SAMPLE);
        ByteBuffer<ByteBuf> wrappedNative = factory.wrap(nativeBuffer);
        ByteBuffer<ByteBuf> empty = factory.copiedBuffer(new byte[0]);
        ByteBuffer<ByteBuf> allocated = factory.buffer();

        try {
            copiedSource[0] = (byte) 'X';
            wrappedSource[0] = (byte) 'W';

            assertThat(copied.toByteArray()).containsExactly(SAMPLE);
            assertThat(copiedNio.toString(StandardCharsets.UTF_8)).isEqualTo("nio-copy");
            assertThat(wrapped.toString(StandardCharsets.UTF_8)).isEqualTo("Wrapped");
            assertThat(wrappedNative.asNativeBuffer()).isSameAs(nativeBuffer);
            assertThat(empty.asNativeBuffer()).isSameAs(Unpooled.EMPTY_BUFFER);
            assertThat(empty.readableBytes()).isZero();
            assertThat(allocated.asNativeBuffer().alloc())
                    .isSameAs(UnpooledByteBufAllocator.DEFAULT);

            ReferenceCounted referenceCounted = (ReferenceCounted) allocated;
            int initialReferences = allocated.asNativeBuffer().refCnt();
            assertThat(referenceCounted.retain()).isSameAs(referenceCounted);
            assertThat(allocated.asNativeBuffer().refCnt()).isEqualTo(initialReferences + 1);
            assertThat(referenceCounted.release()).isFalse();
            assertThat(allocated.asNativeBuffer().refCnt()).isEqualTo(initialReferences);

            assertThat(NettyByteBufferFactory.DEFAULT.getNativeAllocator())
                    .isSameAs(ByteBufAllocator.DEFAULT);
        } finally {
            release(copied.asNativeBuffer());
            release(copiedNio.asNativeBuffer());
            release(wrapped.asNativeBuffer());
            release(nativeBuffer);
            release(allocated.asNativeBuffer());
        }
    }

    @Test
    void streamsAndComposesByteBufferContent() throws IOException {
        NettyByteBufferFactory factory =
                new NettyByteBufferFactory(UnpooledByteBufAllocator.DEFAULT);
        ByteBuffer<ByteBuf> streamBuffer = factory.buffer();

        try {
            try (OutputStream output = streamBuffer.toOutputStream()) {
                output.write("streamed".getBytes(StandardCharsets.UTF_8));
            }
            try (InputStream input = streamBuffer.toInputStream()) {
                assertThat(input.readAllBytes())
                        .containsExactly("streamed".getBytes(StandardCharsets.UTF_8));
            }
        } finally {
            release(streamBuffer.asNativeBuffer());
        }

        ByteBuffer<ByteBuf> destination = factory.copiedBuffer(new byte[] {'A'});
        ByteBuffer<ByteBuf> nettySource = factory.copiedBuffer(new byte[] {'B'});
        ByteBuf nettySourceDelegate = nettySource.asNativeBuffer();
        ByteBuffer<?> jdkSource =
                ReadBufferFactory.getJdkFactory().adapt(new byte[] {'C'}).toByteBuffer();

        try {
            destination.write(nettySource, jdkSource);
            destination.write(StandardCharsets.UTF_8.encode("D"));

            assertThat(destination.toByteArray())
                    .containsExactly("ABCD".getBytes(StandardCharsets.UTF_8));
            assertThat(destination.asNativeBuffer()).isInstanceOf(CompositeByteBuf.class);
        } finally {
            release(destination.asNativeBuffer());
            release(nettySourceDelegate);
        }
    }

    @Test
    void createsReadBuffersFromStreamsChannelsAndOtherBufferTypes() throws IOException {
        NettyReadBufferFactory factory =
                NettyReadBufferFactory.of(UnpooledByteBufAllocator.DEFAULT);

        try (ReadBuffer empty = factory.createEmpty();
                ReadBuffer utf8 = factory.copyOf("Gr\u00fc\u00dfe", StandardCharsets.UTF_8);
                ReadBuffer latin = factory.copyOf("caf\u00e9", StandardCharsets.ISO_8859_1);
                InputStream input = new ByteArrayInputStream(SAMPLE);
                ReadBuffer fromStream = factory.copyOf(input);
                ReadBuffer fromNio = factory.copyOf(StandardCharsets.UTF_8.encode("nio"));
                ReadBuffer adaptedNio = factory.adapt(StandardCharsets.UTF_8.encode("adapted"));
                ReadBuffer adaptedArray = factory.adapt("array".getBytes(StandardCharsets.UTF_8))) {
            assertThat(empty.readable()).isZero();
            assertThat(utf8.toString(StandardCharsets.UTF_8)).isEqualTo("Gr\u00fc\u00dfe");
            assertThat(latin.toString(StandardCharsets.ISO_8859_1)).isEqualTo("caf\u00e9");
            assertThat(fromStream.toArray()).containsExactly(SAMPLE);
            assertThat(fromNio.toString(StandardCharsets.UTF_8)).isEqualTo("nio");
            assertThat(adaptedNio.toString(StandardCharsets.UTF_8)).isEqualTo("adapted");
            assertThat(adaptedArray.toString(StandardCharsets.UTF_8)).isEqualTo("array");
        }

        try (InMemoryScatteringByteChannel channel =
                        InMemoryScatteringByteChannel.reading("channel-data");
                ReadBuffer fromChannel = factory.copyOf(channel, 7)) {
            assertThat(fromChannel.toString(StandardCharsets.UTF_8)).isEqualTo("channel");
        }
        try (InMemoryScatteringByteChannel channel =
                        InMemoryScatteringByteChannel.returningZeroOnce();
                ReadBuffer noDataYet = factory.copyOf(channel, 8)) {
            assertThat(noDataYet.readable()).isZero();
        }
        try (InMemoryScatteringByteChannel channel = InMemoryScatteringByteChannel.atEnd()) {
            assertThat(factory.copyOf(channel, 8)).isNull();
        }

        ByteBuf nativeInput = Unpooled.copiedBuffer(SAMPLE);
        try (ReadBuffer adaptedNative = factory.adapt(nativeInput)) {
            assertThat(adaptedNative.toArray()).containsExactly(SAMPLE);
        }

        ByteBuffer<ByteBuf> nettyBuffer = NettyByteBufferFactory.DEFAULT.copiedBuffer(SAMPLE);
        try (ReadBuffer adaptedMicronautBuffer = factory.adapt(nettyBuffer)) {
            assertThat(adaptedMicronautBuffer.toArray()).containsExactly(SAMPLE);
        }

        ByteBuffer<?> jdkBuffer =
                ReadBufferFactory.getJdkFactory().adapt(SAMPLE.clone()).toByteBuffer();
        try (ReadBuffer adaptedJdkBuffer = factory.adapt(jdkBuffer)) {
            assertThat(adaptedJdkBuffer.toArray()).containsExactly(SAMPLE);
        }
    }

    @Test
    void supportsReadBufferOwnershipViewsAndConsumption() throws IOException {
        NettyReadBufferFactory factory =
                NettyReadBufferFactory.of(UnpooledByteBufAllocator.DEFAULT);

        try (ReadBuffer source = factory.adapt("abcdef".getBytes(StandardCharsets.UTF_8));
                ReadBuffer duplicate = source.duplicate();
                ReadBuffer prefix = source.split(2);
                ReadBuffer moved = source.move()) {
            assertThat(prefix.toString(StandardCharsets.UTF_8)).isEqualTo("ab");
            assertThat(duplicate.toString(StandardCharsets.UTF_8)).isEqualTo("abcdef");
            assertThat(moved.toString()).contains("len=4").contains("cdef");
            assertThat(moved.toString(StandardCharsets.UTF_8)).isEqualTo("cdef");
        }

        try (ReadBuffer buffer = factory.adapt("offset".getBytes(StandardCharsets.UTF_8))) {
            byte[] destination = new byte[10];
            buffer.toArray(destination, 2);
            assertThat(destination)
                    .containsExactly(
                            (byte) 0,
                            (byte) 0,
                            (byte) 'o',
                            (byte) 'f',
                            (byte) 'f',
                            (byte) 's',
                            (byte) 'e',
                            (byte) 't',
                            (byte) 0,
                            (byte) 0);
        }

        ByteBuffer<?> converted;
        try (ReadBuffer buffer = factory.adapt(SAMPLE.clone())) {
            converted = buffer.toByteBuffer();
        }
        try {
            assertThat(converted.toByteArray()).containsExactly(SAMPLE);
        } finally {
            ((ReferenceCounted) converted).release();
        }

        try (ReadBuffer buffer = factory.adapt(SAMPLE.clone());
                InputStream input = buffer.toInputStream()) {
            assertThat(input.readAllBytes()).containsExactly(SAMPLE);
        }

        try (ReadBuffer heap = factory.adapt(SAMPLE.clone())) {
            String decoded = heap.useFastHeapBuffer(
                    nioBuffer -> StandardCharsets.UTF_8.decode(nioBuffer).toString());
            assertThat(decoded).isEqualTo("netty-buffer");
        }

        ByteBuf directInput = UnpooledByteBufAllocator.DEFAULT.directBuffer();
        directInput.writeBytes(SAMPLE);
        try (ReadBuffer direct = factory.adapt(directInput)) {
            Integer fastHeapResult =
                    direct.useFastHeapBuffer(nioBuffer -> nioBuffer.remaining());
            assertThat(fastHeapResult).isNull();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            direct.transferTo(output);
            assertThat(output.toByteArray()).containsExactly(SAMPLE);
        }
    }

    @Test
    void buffersOutputAndComposesReadBuffers() throws IOException {
        NettyReadBufferFactory factory =
                NettyReadBufferFactory.of(UnpooledByteBufAllocator.DEFAULT);

        try (ReadBuffer buffered = factory.buffer(
                output -> output.write("writer".getBytes(StandardCharsets.UTF_8)))) {
            assertThat(buffered.toString(StandardCharsets.UTF_8)).isEqualTo("writer");
        }

        ReadBuffer finished;
        try (ReadBufferFactory.BufferingOutputStream buffering = factory.outputStreamBuffer()) {
            buffering.stream().write("output-stream".getBytes(StandardCharsets.UTF_8));
            assertThat(buffering.stream()).isSameAs(buffering.stream());
            finished = buffering.finishBuffer();
        }
        try (finished) {
            assertThat(finished.toString(StandardCharsets.UTF_8)).isEqualTo("output-stream");
        }

        try (ReadBuffer emptyComposition = factory.compose(List.of())) {
            assertThat(emptyComposition.readable()).isZero();
        }

        ReadBuffer only = factory.adapt("only".getBytes(StandardCharsets.UTF_8));
        ReadBuffer oneBufferComposition = factory.compose(List.of(only));
        assertThat(oneBufferComposition).isSameAs(only);
        try (oneBufferComposition) {
            assertThat(oneBufferComposition.toString(StandardCharsets.UTF_8)).isEqualTo("only");
        }

        ReadBuffer first = factory.adapt("one-".getBytes(StandardCharsets.UTF_8));
        ReadBuffer second = factory.adapt("two".getBytes(StandardCharsets.UTF_8));
        try (ReadBuffer composition = factory.compose(List.of(first, second))) {
            assertThat(composition.toString(StandardCharsets.UTF_8)).isEqualTo("one-two");
        }
    }

    @Test
    void convertsBetweenNettyMicronautAndJdkBufferTypes() {
        assertThat(NettyByteBufferFactory.DEFAULT.getNativeAllocator()).isNotNull();
        MutableConversionService conversionService = MutableConversionService.create();

        assertThat(conversionService.canConvert(byte[].class, ByteBuf.class)).isTrue();
        ByteBuf fromArray = conversionService.convertRequired(SAMPLE.clone(), ByteBuf.class);
        ByteBuf fromNio = conversionService.convertRequired(
                StandardCharsets.UTF_8.encode("nio-conversion"), ByteBuf.class);
        ByteBuf source = Unpooled.copiedBuffer(SAMPLE);

        try {
            assertThat(fromArray.toString(StandardCharsets.UTF_8)).isEqualTo("netty-buffer");
            assertThat(fromNio.toString(StandardCharsets.UTF_8)).isEqualTo("nio-conversion");
            assertThat(conversionService.convertRequired(source, byte[].class))
                    .containsExactly(SAMPLE);
            assertThat(conversionService.convertRequired(source, String.class))
                    .isEqualTo("netty-buffer");
            assertThat(conversionService.convertRequired(source, CharSequence.class).toString())
                    .isEqualTo("netty-buffer");

            ByteBuffer<?> micronautBuffer =
                    conversionService.convertRequired(source, ByteBuffer.class);
            assertThat(micronautBuffer.asNativeBuffer()).isSameAs(source);
            assertThat(conversionService.convertRequired(micronautBuffer, ByteBuf.class))
                    .isSameAs(source);
        } finally {
            release(fromArray);
            release(fromNio);
            release(source);
        }

        CompositeByteBuf composite = Unpooled.compositeBuffer();
        composite.addComponents(
                true,
                Unpooled.copiedBuffer("composite".getBytes(StandardCharsets.UTF_8)),
                Unpooled.copiedBuffer("-buffer".getBytes(StandardCharsets.UTF_8)));
        try {
            assertThat(conversionService.convertRequired(composite, CharSequence.class).toString())
                    .isEqualTo("composite-buffer");
        } finally {
            release(composite);
        }
    }

    @Test
    void convertsReadBuffersToNettyBuffersWithoutLosingContent() {
        NettyReadBufferFactory nettyFactory =
                NettyReadBufferFactory.of(UnpooledByteBufAllocator.DEFAULT);

        ByteBuf original = Unpooled.copiedBuffer(SAMPLE);
        ByteBuf convertedNetty;
        try (ReadBuffer nettyReadBuffer = nettyFactory.adapt(original)) {
            convertedNetty = NettyReadBufferFactory.toByteBuf(nettyReadBuffer);
        }
        try {
            assertThat(convertedNetty).isSameAs(original);
            assertThat(convertedNetty.toString(StandardCharsets.UTF_8))
                    .isEqualTo("netty-buffer");
        } finally {
            release(convertedNetty);
        }

        ReadBuffer jdkReadBuffer = ReadBufferFactory.getJdkFactory().adapt(SAMPLE.clone());
        ByteBuf convertedJdk = NettyReadBufferFactory.toByteBuf(jdkReadBuffer);
        try {
            assertThat(convertedJdk.toString(StandardCharsets.UTF_8))
                    .isEqualTo("netty-buffer");
        } finally {
            release(convertedJdk);
        }
    }

    @Test
    void providesAContainerManagedByteBufferFactory() {
        try (ApplicationContext context =
                ApplicationContext.builder().eagerBeansEnabled(false).start()) {
            NettyByteBufferFactory factory = context.getBean(NettyByteBufferFactory.class);
            ByteBuffer<ByteBuf> buffer = factory.buffer(SAMPLE.length);

            try {
                buffer.write(SAMPLE.clone());
                byte[] copy = new byte[SAMPLE.length];
                buffer.read(copy);

                assertThat(context.getBean(NettyByteBufferFactory.class)).isSameAs(factory);
                assertThat(factory.getNativeAllocator()).isSameAs(ByteBufAllocator.DEFAULT);
                assertThat(copy).containsExactly(SAMPLE);
                assertThat(buffer.readableBytes()).isZero();
            } finally {
                release(buffer.asNativeBuffer());
            }
        }
    }

    @Test
    void bindsDefaultAllocatorConfigurationToNettySystemProperties() {
        Map<String, Object> properties = Map.ofEntries(
                Map.entry("netty.default.allocator.num-heap-arenas", 1),
                Map.entry("netty.default.allocator.num-direct-arenas", 2),
                Map.entry("netty.default.allocator.page-size", 8192),
                Map.entry("netty.default.allocator.max-order", 4),
                Map.entry("netty.default.allocator.chunk-size", 131072),
                Map.entry("netty.default.allocator.small-cache-size", 64),
                Map.entry("netty.default.allocator.normal-cache-size", 32),
                Map.entry("netty.default.allocator.use-cache-for-all-threads", false),
                Map.entry("netty.default.allocator.max-cached-buffer-capacity", 32768),
                Map.entry("netty.default.allocator.cache-trim-interval", 1024),
                Map.entry("netty.default.allocator.max-cached-byte-buffers-per-chunk", 128));
        Map<String, String> expectedSystemProperties = Map.ofEntries(
                Map.entry("io.netty.allocator.numHeapArenas", "1"),
                Map.entry("io.netty.allocator.numDirectArenas", "2"),
                Map.entry("io.netty.allocator.pageSize", "8192"),
                Map.entry("io.netty.allocator.maxOrder", "4"),
                Map.entry("io.netty.allocator.chunkSize", "131072"),
                Map.entry("io.netty.allocator.smallCacheSize", "64"),
                Map.entry("io.netty.allocator.normalCacheSize", "32"),
                Map.entry("io.netty.allocator.useCacheForAllThreads", "false"),
                Map.entry("io.netty.allocator.maxCachedBufferCapacity", "32768"),
                Map.entry("io.netty.allocator.cacheTrimInterval", "1024"),
                Map.entry("io.netty.allocator.maxCachedByteBuffersPerChunk", "128"));
        Map<String, String> originalSystemProperties = new LinkedHashMap<>();
        expectedSystemProperties.keySet().forEach(
                name -> originalSystemProperties.put(name, System.getProperty(name)));

        try (ApplicationContext context = ApplicationContext.builder(properties)
                .eagerBeansEnabled(false)
                .start()) {
            assertThat(context.getBean(ByteBufAllocatorConfiguration.class)).isNotNull();
            expectedSystemProperties.forEach(
                    (name, value) -> assertThat(System.getProperty(name)).isEqualTo(value));
        } finally {
            restoreSystemProperties(originalSystemProperties);
        }
    }

    private static void restoreSystemProperties(Map<String, String> properties) {
        properties.forEach((name, value) -> {
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        });
    }

    private static void release(ByteBuf buffer) {
        if (buffer.refCnt() > 0) {
            buffer.release();
        }
    }

    private static final class InMemoryScatteringByteChannel
            implements ScatteringByteChannel {
        private final byte[] content;
        private boolean open = true;
        private boolean returnZero;
        private int position;

        private InMemoryScatteringByteChannel(byte[] content, boolean returnZero) {
            this.content = content;
            this.returnZero = returnZero;
        }

        static InMemoryScatteringByteChannel reading(String content) {
            return new InMemoryScatteringByteChannel(
                    content.getBytes(StandardCharsets.UTF_8), false);
        }

        static InMemoryScatteringByteChannel returningZeroOnce() {
            return new InMemoryScatteringByteChannel(SAMPLE.clone(), true);
        }

        static InMemoryScatteringByteChannel atEnd() {
            return new InMemoryScatteringByteChannel(new byte[0], false);
        }

        @Override
        public int read(java.nio.ByteBuffer destination) throws IOException {
            ensureOpen();
            if (returnZero) {
                returnZero = false;
                return 0;
            }
            if (position == content.length) {
                return -1;
            }
            int count = Math.min(destination.remaining(), content.length - position);
            destination.put(content, position, count);
            position += count;
            return count;
        }

        @Override
        public long read(java.nio.ByteBuffer[] destinations, int offset, int length)
                throws IOException {
            ensureOpen();
            long total = 0;
            for (int i = offset; i < offset + length; i++) {
                int read = read(destinations[i]);
                if (read < 0) {
                    return total == 0 ? -1 : total;
                }
                total += read;
                if (read == 0 || position == content.length) {
                    break;
                }
            }
            return total;
        }

        @Override
        public long read(java.nio.ByteBuffer[] destinations) throws IOException {
            return read(destinations, 0, destinations.length);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        private void ensureOpen() throws ClosedChannelException {
            if (!open) {
                throw new ClosedChannelException();
            }
        }
    }
}
