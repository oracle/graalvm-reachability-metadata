/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.jpountz.lz4.LZ4FrameInputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import net.jpountz.lz4.LZ4FrameOutputStream.BD;
import net.jpountz.lz4.LZ4FrameOutputStream.BLOCKSIZE;
import net.jpountz.lz4.LZ4FrameOutputStream.FLG;
import net.jpountz.lz4.LZ4FrameOutputStream.FLG.Bits;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;

class Lz4FrameStreamApiTest {
    private static final byte[] INPUT = ("The LZ4 frame API supports streaming data and optional "
            + "content size and checksum descriptors.").getBytes(StandardCharsets.UTF_8);

    @Test
    void frameOutputConstructorsAndWriteMethodsProduceReadableFrames() throws IOException {
        assertThat(readFrame(writeDefaultFrame())).isEqualTo(INPUT);
        assertThat(readFrame(writeBlockSizeFrame())).isEqualTo(INPUT);
        assertThat(readFrame(writeFeatureFrame())).isEqualTo(INPUT);
        assertThat(readFrame(writeKnownSizeConvenienceFrame())).isEqualTo(INPUT);
        assertThat(readFrame(writeKnownSizeFrame())).isEqualTo(INPUT);
    }

    @Test
    void frameInputConstructorsReadFramesAndExposeNavigationState() throws IOException {
        byte[] unknownFrame = writeDefaultFrame();
        byte[] knownFrame = writeKnownSizeFrame();
        byte[] concatenated = new byte[unknownFrame.length + knownFrame.length];
        System.arraycopy(unknownFrame, 0, concatenated, 0, unknownFrame.length);
        System.arraycopy(knownFrame, 0, concatenated, unknownFrame.length, knownFrame.length);

        LZ4FrameInputStream allFrames = new LZ4FrameInputStream(new ByteArrayInputStream(concatenated));
        assertThat(readAll(allFrames)).isEqualTo(concat(INPUT, INPUT));

        LZ4FrameInputStream singleFrame = new LZ4FrameInputStream(new ByteArrayInputStream(knownFrame), true);
        assertThat(singleFrame.available()).isZero();
        assertThat(singleFrame.isExpectedContentSizeDefined()).isTrue();
        assertThat(singleFrame.getExpectedContentSize()).isEqualTo(INPUT.length);
        assertThat(singleFrame.markSupported()).isFalse();
        assertThatThrownBy(() -> singleFrame.mark(10)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(singleFrame::reset).isInstanceOf(UnsupportedOperationException.class);
        byte[] first = new byte[5];
        assertThat(singleFrame.read(first, 0, first.length)).isEqualTo(first.length);
        assertThat(first).isEqualTo(java.util.Arrays.copyOf(INPUT, first.length));
        assertThat(singleFrame.skip(3)).isEqualTo(3);
        byte[] remainder = new byte[INPUT.length - 8];
        int offset = 0;
        int read;
        while ((read = singleFrame.read(remainder, offset, remainder.length - offset)) != -1) {
            offset += read;
        }
        assertThat(concat(first, concat(new byte[]{INPUT[5], INPUT[6], INPUT[7]}, remainder))).isEqualTo(INPUT);
        assertThat(singleFrame.read()).isEqualTo(-1);
        singleFrame.close();

        LZ4FrameInputStream custom = new LZ4FrameInputStream(new ByteArrayInputStream(unknownFrame),
                LZ4Factory.safeInstance().safeDecompressor(), XXHashFactory.safeInstance().hash32());
        assertThat(custom.isExpectedContentSizeDefined()).isFalse();
        assertThat(readAll(custom)).isEqualTo(INPUT);

        LZ4FrameInputStream customSingle = new LZ4FrameInputStream(new ByteArrayInputStream(knownFrame),
                LZ4Factory.safeInstance().safeDecompressor(), XXHashFactory.safeInstance().hash32(), true);
        assertThat(customSingle.getExpectedContentSize()).isEqualTo(INPUT.length);
        assertThat(readAll(customSingle)).isEqualTo(INPUT);
    }

    @Test
    void expectedContentSizeSkipsSkippableFramesBeforeReadingPayload() throws IOException {
        byte[] frame = writeKnownSizeFrame();
        ByteBuffer skippable = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        skippable.putInt(0x184D2A50);
        skippable.putInt(4);
        skippable.putInt(0xCAFEBABE);
        byte[] prefix = skippable.array();
        byte[] combined = new byte[prefix.length + frame.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(frame, 0, combined, prefix.length, frame.length);

        LZ4FrameInputStream input = new LZ4FrameInputStream(new ByteArrayInputStream(combined), true);
        assertThat(input.getExpectedContentSize()).isEqualTo(INPUT.length);
        assertThat(readAll(input)).isEqualTo(INPUT);
    }

    @Test
    void frameDescriptorTypesRoundTripTheirPublicRepresentations() {
        assertThat(BLOCKSIZE.values()).containsExactly(BLOCKSIZE.SIZE_64KB, BLOCKSIZE.SIZE_256KB,
                BLOCKSIZE.SIZE_1MB, BLOCKSIZE.SIZE_4MB);
        assertThat(BLOCKSIZE.valueOf("SIZE_1MB")).isEqualTo(BLOCKSIZE.SIZE_1MB);
        assertThat(BLOCKSIZE.valueOf(5)).isEqualTo(BLOCKSIZE.SIZE_256KB);
        assertThat(BLOCKSIZE.SIZE_64KB.getIndicator()).isEqualTo(4);

        FLG flags = new FLG(1, Bits.BLOCK_INDEPENDENCE, Bits.CONTENT_SIZE);
        assertThat(flags.getVersion()).isEqualTo(1);
        assertThat(flags.isEnabled(Bits.CONTENT_SIZE)).isTrue();
        assertThat(flags.isEnabled(Bits.BLOCK_CHECKSUM)).isFalse();
        FLG decodedFlags = FLG.fromByte(flags.toByte());
        assertThat(decodedFlags.getVersion()).isEqualTo(flags.getVersion());
        assertThat(decodedFlags.isEnabled(Bits.CONTENT_SIZE)).isTrue();
        assertThat(Bits.valueOf("BLOCK_INDEPENDENCE")).isEqualTo(Bits.BLOCK_INDEPENDENCE);
        assertThat(Bits.values()).contains(Bits.CONTENT_CHECKSUM, Bits.BLOCK_CHECKSUM);

        BD descriptor = BD.fromByte((byte) (BLOCKSIZE.SIZE_1MB.getIndicator() << 4));
        assertThat(descriptor.getBlockMaximumSize()).isEqualTo(1 << 20);
        assertThat(descriptor.toByte()).isEqualTo((byte) 0x60);
    }

    private static byte[] writeDefaultFrame() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (LZ4FrameOutputStream stream = new LZ4FrameOutputStream(output)) {
            stream.write(INPUT, 0, 4);
            stream.write(INPUT, 4, INPUT.length - 5);
            stream.write(INPUT[INPUT.length - 1]);
            stream.flush();
        }
        return output.toByteArray();
    }

    private static byte[] writeBlockSizeFrame() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (LZ4FrameOutputStream stream = new LZ4FrameOutputStream(output, BLOCKSIZE.SIZE_64KB)) {
            stream.write(INPUT);
        }
        return output.toByteArray();
    }

    private static byte[] writeFeatureFrame() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (LZ4FrameOutputStream stream = new LZ4FrameOutputStream(output, BLOCKSIZE.SIZE_64KB,
                Bits.BLOCK_INDEPENDENCE)) {
            stream.write(INPUT);
        }
        return output.toByteArray();
    }

    private static byte[] writeKnownSizeConvenienceFrame() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (LZ4FrameOutputStream stream = new LZ4FrameOutputStream(output, BLOCKSIZE.SIZE_64KB, INPUT.length,
                Bits.BLOCK_INDEPENDENCE, Bits.CONTENT_SIZE)) {
            stream.write(INPUT);
        }
        return output.toByteArray();
    }

    private static byte[] writeKnownSizeFrame() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (LZ4FrameOutputStream stream = new LZ4FrameOutputStream(output, BLOCKSIZE.SIZE_64KB, INPUT.length,
                LZ4Factory.safeInstance().fastCompressor(), XXHashFactory.safeInstance().hash32(),
                Bits.BLOCK_INDEPENDENCE, Bits.CONTENT_SIZE, Bits.CONTENT_CHECKSUM, Bits.BLOCK_CHECKSUM)) {
            stream.write(INPUT);
        }
        return output.toByteArray();
    }

    private static byte[] readFrame(byte[] encoded) throws IOException {
        return readAll(new LZ4FrameInputStream(new ByteArrayInputStream(encoded), true));
    }

    private static byte[] readAll(LZ4FrameInputStream stream) throws IOException {
        try (LZ4FrameInputStream input = stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[13];
            int read;
            while ((read = input.read(buffer, 0, buffer.length)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
