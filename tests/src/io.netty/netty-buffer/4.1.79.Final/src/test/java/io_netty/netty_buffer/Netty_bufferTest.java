/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_buffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocatorMetric;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.buffer.search.AbstractMultiSearchProcessorFactory;
import io.netty.buffer.search.AbstractSearchProcessorFactory;
import io.netty.buffer.search.MultiSearchProcessor;
import io.netty.buffer.search.SearchProcessor;
import org.junit.jupiter.api.Test;

public class Netty_bufferTest {
    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final Charset US_ASCII = StandardCharsets.US_ASCII;

    @Test
    public void byteBufStoresPrimitiveValuesWithIndependentReaderAndWriterIndexes() {
        ByteBuf buffer = Unpooled.buffer(8, 128);
        try {
            assertThat(buffer.readerIndex()).isZero();
            assertThat(buffer.writerIndex()).isZero();
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(8);

            buffer.writeBoolean(true)
                    .writeByte(0x7f)
                    .writeShort(0x1234)
                    .writeShortLE(0x5678)
                    .writeMedium(0x010203)
                    .writeMediumLE(0x040506)
                    .writeInt(0x0a0b0c0d)
                    .writeIntLE(0x11121314)
                    .writeLong(0x0102030405060708L)
                    .writeLongLE(0x1112131415161718L)
                    .writeFloat(3.5f)
                    .writeDouble(9.25d);
            int bytesWritten = buffer.writerIndex();

            assertThat(buffer.readerIndex()).isZero();
            assertThat(buffer.readableBytes()).isEqualTo(bytesWritten);
            assertThat(buffer.getUnsignedByte(1)).isEqualTo((short) 0x7f);
            assertThat(buffer.getUnsignedShort(2)).isEqualTo(0x1234);
            assertThat(buffer.getUnsignedShortLE(4)).isEqualTo(0x5678);
            assertThat(buffer.getUnsignedMedium(6)).isEqualTo(0x010203);
            assertThat(buffer.getUnsignedMediumLE(9)).isEqualTo(0x040506);

            buffer.markReaderIndex();
            assertThat(buffer.readBoolean()).isTrue();
            assertThat(buffer.readByte()).isEqualTo((byte) 0x7f);
            assertThat(buffer.readShort()).isEqualTo((short) 0x1234);
            buffer.resetReaderIndex();
            assertThat(buffer.readerIndex()).isZero();

            assertThat(buffer.readBoolean()).isTrue();
            assertThat(buffer.readByte()).isEqualTo((byte) 0x7f);
            assertThat(buffer.readShort()).isEqualTo((short) 0x1234);
            assertThat(buffer.readShortLE()).isEqualTo((short) 0x5678);
            assertThat(buffer.readUnsignedMedium()).isEqualTo(0x010203);
            assertThat(buffer.readUnsignedMediumLE()).isEqualTo(0x040506);
            assertThat(buffer.readInt()).isEqualTo(0x0a0b0c0d);
            assertThat(buffer.readIntLE()).isEqualTo(0x11121314);
            assertThat(buffer.readLong()).isEqualTo(0x0102030405060708L);
            assertThat(buffer.readLongLE()).isEqualTo(0x1112131415161718L);
            assertThat(buffer.readFloat()).isEqualTo(3.5f);
            assertThat(buffer.readDouble()).isEqualTo(9.25d);
            assertThat(buffer.isReadable()).isFalse();

            buffer.clear();
            buffer.writeCharSequence("netty", US_ASCII);
            assertThat(buffer.toString(US_ASCII)).isEqualTo("netty");
        } finally {
            buffer.release();
        }
    }

    @Test
    public void slicesCopiesDuplicatesAndReadOnlyViewsExposeExpectedReferenceSemantics() {
        ByteBuf source = Unpooled.copiedBuffer("header:payload:tail", UTF_8);
        ByteBuf retainedPayload = null;
        try {
            ByteBuf payloadSlice = source.slice(7, 7);
            assertThat(payloadSlice.toString(UTF_8)).isEqualTo("payload");

            payloadSlice.setByte(0, 'P');
            assertThat(source.toString(7, 7, UTF_8)).isEqualTo("Payload");

            ByteBuf copy = payloadSlice.copy();
            try {
                payloadSlice.setByte(1, 'A');
                assertThat(source.toString(7, 7, UTF_8)).isEqualTo("PAyload");
                assertThat(copy.toString(UTF_8)).isEqualTo("Payload");
            } finally {
                copy.release();
            }

            ByteBuf duplicate = source.duplicate();
            duplicate.readerIndex(7);
            assertThat(source.readerIndex()).isZero();
            assertThat(duplicate.readCharSequence(7, UTF_8).toString()).isEqualTo("PAyload");

            ByteBuf readOnly = source.asReadOnly();
            assertThat(readOnly.isReadOnly()).isTrue();
            assertThat(readOnly.toString(UTF_8)).isEqualTo(source.toString(UTF_8));
            assertThatExceptionOfType(ReadOnlyBufferException.class)
                    .isThrownBy(() -> readOnly.setByte(0, 'x'));

            retainedPayload = source.retainedSlice(7, 7);
            assertThat(source.refCnt()).isEqualTo(2);
            ByteBuf expectedPayload = Unpooled.copiedBuffer("PAyload", UTF_8);
            try {
                assertThat(ByteBufUtil.equals(retainedPayload, expectedPayload)).isTrue();
                assertThat(ByteBufUtil.hashCode(retainedPayload)).isEqualTo(ByteBufUtil.hashCode(expectedPayload));
            } finally {
                expectedPayload.release();
            }
        } finally {
            if (retainedPayload != null) {
                retainedPayload.release();
            }
            source.release();
        }
    }

    @Test
    public void compositeByteBufReadsWritesAndConsolidatesAcrossComponentBoundaries() {
        CompositeByteBuf composite = Unpooled.compositeBuffer(8);
        try {
            ByteBuf first = Unpooled.copiedBuffer("net", UTF_8);
            ByteBuf second = Unpooled.copiedBuffer("ty", UTF_8);
            ByteBuf third = Unpooled.copiedBuffer(new byte[] {0x01, 0x02, 0x03, 0x04});
            composite.addComponents(true, first, second, third);

            assertThat(composite.numComponents()).isEqualTo(3);
            assertThat(composite.toString(0, 5, UTF_8)).isEqualTo("netty");
            assertThat(composite.getInt(3)).isEqualTo(0x74790102);
            assertThat(composite.toComponentIndex(4)).isEqualTo(1);
            assertThat(composite.toByteIndex(2)).isEqualTo(5);

            composite.setByte(0, 'N');
            assertThat(composite.toString(0, 5, UTF_8)).isEqualTo("Netty");

            List<ByteBuf> decomposed = composite.decompose(2, 5);
            assertThat(decomposed).hasSize(3);
            assertThat(decomposed.get(0).toString(UTF_8)).isEqualTo("t");
            assertThat(decomposed.get(1).toString(UTF_8)).isEqualTo("ty");
            assertThat(decomposed.get(2).readableBytes()).isEqualTo(2);

            ByteBuffer[] nioBuffers = composite.nioBuffers(0, composite.readableBytes());
            assertThat(nioBuffers).hasSize(3);

            composite.readerIndex(5);
            composite.discardReadComponents();
            assertThat(composite.readerIndex()).isZero();
            assertThat(composite.readableBytes()).isEqualTo(4);
            assertThat(composite.readInt()).isEqualTo(0x01020304);

            composite.clear().writeBytes(new byte[] {10, 20, 30, 40});
            composite.consolidate();
            assertThat(composite.numComponents()).isEqualTo(1);
            assertThat(ByteBufUtil.getBytes(composite)).containsExactly((byte) 10, (byte) 20, (byte) 30, (byte) 40);
        } finally {
            composite.release();
        }
    }

    @Test
    public void byteBufInputAndOutputStreamsRoundTripDataInputAndDataOutputValues() throws IOException {
        ByteBuf buffer = Unpooled.buffer();
        try {
            ByteBufOutputStream output = new ByteBufOutputStream(buffer);
            output.writeBoolean(true);
            output.writeByte(0x42);
            output.writeShort(0x1234);
            output.writeChar('Z');
            output.writeInt(0x01020304);
            output.writeLong(0x0102030405060708L);
            output.writeFloat(1.25f);
            output.writeDouble(123.5d);
            output.writeUTF("Grüße Netty");
            output.write(new byte[] {9, 8, 7, 6});

            assertThat(output.buffer()).isSameAs(buffer);
            assertThat(output.writtenBytes()).isEqualTo(buffer.readableBytes());

            ByteBufInputStream input = new ByteBufInputStream(buffer, buffer.readableBytes(), false);
            assertThat(input.markSupported()).isTrue();
            assertThat(input.readBoolean()).isTrue();
            assertThat(input.readUnsignedByte()).isEqualTo(0x42);
            input.mark(0);
            assertThat(input.readUnsignedShort()).isEqualTo(0x1234);
            assertThat(input.readChar()).isEqualTo('Z');
            input.reset();
            assertThat(input.readUnsignedShort()).isEqualTo(0x1234);
            assertThat(input.readChar()).isEqualTo('Z');
            assertThat(input.readInt()).isEqualTo(0x01020304);
            assertThat(input.readLong()).isEqualTo(0x0102030405060708L);
            assertThat(input.readFloat()).isEqualTo(1.25f);
            assertThat(input.readDouble()).isEqualTo(123.5d);
            assertThat(input.readUTF()).isEqualTo("Grüße Netty");

            byte[] tail = new byte[4];
            input.readFully(tail);
            assertThat(tail).containsExactly((byte) 9, (byte) 8, (byte) 7, (byte) 6);
            assertThat(input.available()).isZero();
            assertThat(input.read()).isEqualTo(-1);
        } finally {
            buffer.release();
        }
    }

    @Test
    public void byteBufUtilEncodesTextHexDumpsAndComparesBuffers() {
        ByteBuf utf8 = ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, "Netty こんにちは");
        ByteBuf ascii = ByteBufUtil.writeAscii(UnpooledByteBufAllocator.DEFAULT, "Netty");
        ByteBuf hex = null;
        try {
            assertThat(utf8.toString(UTF_8)).isEqualTo("Netty こんにちは");
            assertThat(ByteBufUtil.utf8Bytes("Netty こんにちは")).isEqualTo(utf8.readableBytes());
            assertThat(ByteBufUtil.isText(utf8, UTF_8)).isTrue();
            assertThat(ByteBufUtil.isText(utf8, US_ASCII)).isFalse();
            assertThat(ascii.toString(US_ASCII)).isEqualTo("Netty");
            assertThat(ByteBufUtil.isText(ascii, US_ASCII)).isTrue();

            String hexDump = ByteBufUtil.hexDump(ascii);
            assertThat(hexDump).isEqualTo("4e65747479");
            hex = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(hexDump));
            assertThat(ByteBufUtil.equals(ascii, hex)).isTrue();
            assertThat(ByteBufUtil.compare(ascii, hex)).isZero();
            assertThat(ByteBufUtil.indexOf(utf8, 0, utf8.readableBytes(), (byte) 'N')).isZero();
            assertThat(ByteBufUtil.indexOf(utf8, 0, utf8.readableBytes(), (byte) 'y')).isEqualTo(4);
            assertThat(ByteBufUtil.prettyHexDump(ascii)).contains("4e 65 74 74 79");

            ByteBuf destination = Unpooled.buffer(2, 16);
            try {
                int ensureResult = destination.ensureWritable(8, true);
                assertThat(ByteBufUtil.ensureWritableSuccess(ensureResult)).isTrue();
                ByteBufUtil.writeAscii(destination, "buffer");
                assertThat(ByteBufUtil.getBytes(destination)).containsExactly((byte) 'b', (byte) 'u', (byte) 'f', (byte) 'f', (byte) 'e', (byte) 'r');
            } finally {
                destination.release();
            }
        } finally {
            utf8.release();
            ascii.release();
            if (hex != null) {
                hex.release();
            }
        }
    }

    @Test
    public void pooledAndUnpooledAllocatorsCreateHeapDirectAndCompositeBuffers() {
        PooledByteBufAllocator pooledAllocator = new PooledByteBufAllocator(true);
        ByteBuf heap = pooledAllocator.heapBuffer(4, 64);
        ByteBuf direct = pooledAllocator.directBuffer(4, 64);
        CompositeByteBuf composite = pooledAllocator.compositeHeapBuffer(4);
        try {
            assertThat(pooledAllocator.isDirectBufferPooled()).isEqualTo(pooledAllocator.numDirectArenas() > 0);
            assertThat(heap.isDirect()).isFalse();
            assertThat(direct.isDirect()).isTrue();

            heap.writeInt(0x01020304);
            heap.ensureWritable(32);
            assertThat(heap.capacity()).isGreaterThanOrEqualTo(36);
            direct.writeBytes(heap, 0, 4);
            assertThat(direct.readInt()).isEqualTo(0x01020304);

            composite.addComponents(true,
                    UnpooledByteBufAllocator.DEFAULT.heapBuffer(2).writeBytes(new byte[] {1, 2}),
                    UnpooledByteBufAllocator.DEFAULT.heapBuffer(2).writeBytes(new byte[] {3, 4}));
            assertThat(composite.readInt()).isEqualTo(0x01020304);

            PooledByteBufAllocatorMetric metric = pooledAllocator.metric();
            assertThat(metric.numHeapArenas()).isEqualTo(pooledAllocator.numHeapArenas());
            assertThat(metric.numDirectArenas()).isEqualTo(pooledAllocator.numDirectArenas());
            assertThat(metric.chunkSize()).isEqualTo(pooledAllocator.chunkSize());
            assertThat(metric.toString()).contains("usedHeapMemory");
            assertThat(pooledAllocator.trimCurrentThreadCache()).isIn(true, false);
        } finally {
            composite.release();
            direct.release();
            heap.release();
            pooledAllocator.freeThreadLocalCache();
        }
    }

    @Test
    public void searchProcessorFactoriesFindSingleAndMultipleNeedles() {
        ByteBuf haystack = Unpooled.copiedBuffer("xx-first-yy-second-zz", UTF_8);
        try {
            assertFoundAt(AbstractSearchProcessorFactory.newKmpSearchProcessorFactory(bytes("first")).newSearchProcessor(), haystack, "first", 3);
            assertFoundAt(AbstractSearchProcessorFactory.newBitapSearchProcessorFactory(bytes("second")).newSearchProcessor(), haystack, "second", 12);

            MultiSearchProcessor multiSearch = AbstractMultiSearchProcessorFactory
                    .newAhoCorasicSearchProcessorFactory(bytes("absent"), bytes("second"), bytes("first"))
                    .newSearchProcessor();
            int lastNeedleByte = haystack.forEachByte(multiSearch);
            assertThat(lastNeedleByte).isEqualTo(7);
            assertThat(multiSearch.getFoundNeedleId()).isEqualTo(2);

            multiSearch.reset();
            ByteBuf laterWindow = haystack.slice(9, haystack.readableBytes() - 9);
            int secondLastByte = laterWindow.forEachByte(multiSearch);
            assertThat(secondLastByte).isEqualTo(8);
            assertThat(multiSearch.getFoundNeedleId()).isEqualTo(1);
        } finally {
            haystack.release();
        }
    }

    @Test
    public void wrappedCopiedUnmodifiableAndUnreleasableBuffersHaveDistinctMutationAndLifecycleBehavior() {
        byte[] backingArray = new byte[] {'n', 'e', 't', 't', 'y'};
        ByteBuf wrapped = Unpooled.wrappedBuffer(backingArray);
        ByteBuf copied = Unpooled.copiedBuffer(backingArray);
        ByteBuf unmodifiable = Unpooled.wrappedUnmodifiableBuffer(copied.duplicate());
        ByteBuf unreleasable = Unpooled.unreleasableBuffer(wrapped.duplicate());
        try {
            backingArray[0] = 'N';
            assertThat(wrapped.toString(UTF_8)).isEqualTo("Netty");
            assertThat(copied.toString(UTF_8)).isEqualTo("netty");

            assertThatThrownBy(() -> unmodifiable.setByte(0, 'x'))
                    .isInstanceOf(ReadOnlyBufferException.class);

            assertThat(unreleasable.release()).isFalse();
            assertThat(unreleasable.refCnt()).isEqualTo(1);
            unreleasable.setByte(4, '!');
            assertThat(wrapped.toString(UTF_8)).isEqualTo("Nett!");
        } finally {
            copied.release();
            wrapped.release();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(UTF_8);
    }

    private static void assertFoundAt(SearchProcessor processor, ByteBuf haystack, String needle, int expectedStartIndex) {
        int lastNeedleByte = haystack.forEachByte(processor);
        assertThat(lastNeedleByte - bytes(needle).length + 1).isEqualTo(expectedStartIndex);
    }
}
