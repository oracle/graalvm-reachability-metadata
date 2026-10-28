/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Compresses database pages with the static one-shot `Zstd` block API on caller-managed buffers:
 * `Zstd.compressByteArray` and `Zstd.decompressByteArray` at offsets into a shared arena,
 * `Zstd.compress` and `Zstd.decompress` between direct `ByteBuffer`s, raw-dictionary
 * `Zstd.compressUsingDict`, and the `Zstd` error codes those calls report.
 *
 * A small storage engine keeps fixed-size row pages compressed in one segment image: every page
 * becomes its own checksummed frame appended to the arena, and an index of (offset, length)
 * slots lets a reader inflate any single page into a slab of its page cache without copying.
 * Hot pages are also kept off-heap, and freshly formatted pages are compressed against the empty
 * page template so the shared header layout costs almost nothing.
 */
class PageArenaBlockCompressionTest {

    private static final int PAGE_BYTES = 8 * 1024;

    private static final int PAGE_COUNT = 24;

    private static final int ROW_BYTES = 64;

    private static final int HEADER_BYTES = 128;

    private static final int LEVEL = 5;

    /** Where one compressed page lives inside the segment arena. */
    record PageSlot(int pageNo, int offset, int length) {
    }

    /** Appends compressed pages to one arena and inflates them back into page-cache slabs. */
    static final class SegmentImage {

        private final byte[] arena;

        private final List<PageSlot> slots = new ArrayList<>();

        private int used;

        SegmentImage(int pageCapacity) {
            arena = new byte[Math.toIntExact(pageCapacity * Zstd.compressBound(PAGE_BYTES))];
        }

        PageSlot append(byte[] heapFile, int pageNo) {
            long written = Zstd.compressByteArray(arena, used, arena.length - used,
                    heapFile, pageNo * PAGE_BYTES, PAGE_BYTES, LEVEL, true);
            PageSlot slot = new PageSlot(pageNo, used, Math.toIntExact(written));
            used += slot.length();
            slots.add(slot);
            return slot;
        }

        void readInto(PageSlot slot, byte[] slab, int slabOffset) {
            long declared = Zstd.getFrameContentSize(arena, slot.offset(), slot.length());
            assertThat(declared).isEqualTo(PAGE_BYTES);
            long read = Zstd.decompressByteArray(slab, slabOffset, PAGE_BYTES,
                    arena, slot.offset(), slot.length());
            assertThat(read).isEqualTo(PAGE_BYTES);
        }

        byte[] arena() {
            return arena;
        }

        int used() {
            return used;
        }

        List<PageSlot> slots() {
            return slots;
        }
    }

    @Test
    void pagesAreReadBackIndividuallyFromTheSharedArena() {
        byte[] heapFile = heapFile(PAGE_COUNT);
        SegmentImage segment = new SegmentImage(PAGE_COUNT);
        for (int pageNo = 0; pageNo < PAGE_COUNT; pageNo++) {
            segment.append(heapFile, pageNo);
        }

        assertThat(segment.used()).isLessThan(heapFile.length / 3);
        int expectedOffset = 0;
        for (PageSlot slot : segment.slots()) {
            assertThat(slot.offset()).isEqualTo(expectedOffset);
            expectedOffset += slot.length();
        }

        byte[] cacheSlab = new byte[4 * PAGE_BYTES];
        int[] wanted = {17, 3, 22, 0};
        for (int frame = 0; frame < wanted.length; frame++) {
            segment.readInto(segment.slots().get(wanted[frame]), cacheSlab, frame * PAGE_BYTES);
        }
        for (int frame = 0; frame < wanted.length; frame++) {
            int pageStart = wanted[frame] * PAGE_BYTES;
            assertThat(Arrays.equals(cacheSlab, frame * PAGE_BYTES, (frame + 1) * PAGE_BYTES,
                    heapFile, pageStart, pageStart + PAGE_BYTES)).isTrue();
        }
        String firstRow = new String(cacheSlab, 2 * PAGE_BYTES + HEADER_BYTES, ROW_BYTES,
                StandardCharsets.US_ASCII);
        assertThat(firstRow).startsWith("row=22-000|");
    }

    @Test
    void damagedPagesAreRejectedWithZstdErrorCodes() {
        byte[] heapFile = heapFile(4);
        SegmentImage segment = new SegmentImage(4);
        for (int pageNo = 0; pageNo < 4; pageNo++) {
            segment.append(heapFile, pageNo);
        }
        PageSlot victim = segment.slots().get(2);
        byte[] slab = new byte[PAGE_BYTES];

        segment.arena()[victim.offset() + victim.length() - 1] ^= 0x5A;
        assertThatThrownBy(() -> segment.readInto(victim, slab, 0))
                .isInstanceOfSatisfying(ZstdException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errChecksumWrong()));
        segment.arena()[victim.offset() + victim.length() - 1] ^= 0x5A;
        segment.readInto(victim, slab, 0);
        assertThat(Arrays.equals(slab, 0, PAGE_BYTES, heapFile, 2 * PAGE_BYTES, 3 * PAGE_BYTES)).isTrue();

        byte[] tooSmallSlab = new byte[PAGE_BYTES / 2];
        assertThatThrownBy(() -> Zstd.decompressByteArray(tooSmallSlab, 0, tooSmallSlab.length,
                segment.arena(), victim.offset(), victim.length()))
                .isInstanceOfSatisfying(ZstdException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errDstSizeTooSmall()));

        byte[] noise = new byte[PAGE_BYTES];
        new Random(7).nextBytes(noise);
        byte[] tightSlot = new byte[PAGE_BYTES / 4];
        assertThatThrownBy(() -> Zstd.compressByteArray(tightSlot, 0, tightSlot.length,
                noise, 0, noise.length, LEVEL))
                .isInstanceOfSatisfying(ZstdException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(Zstd.errDstSizeTooSmall()));
        assertThat(Zstd.isError(-Zstd.errDstSizeTooSmall())).isTrue();
        assertThat(Zstd.getErrorName(-Zstd.errDstSizeTooSmall())).containsIgnoringCase("too small");
    }

    @Test
    void hotPagesAreCompressedBetweenDirectBuffers() {
        byte[] heapFile = heapFile(2);
        ByteBuffer hotPage = ByteBuffer.allocateDirect(PAGE_BYTES);
        hotPage.put(heapFile, PAGE_BYTES, PAGE_BYTES).flip();

        ByteBuffer frame = ByteBuffer.allocateDirect(Math.toIntExact(Zstd.compressBound(PAGE_BYTES)));
        int written = Zstd.compress(frame, hotPage, LEVEL, true);
        assertThat(written).isEqualTo(frame.position()).isLessThan(PAGE_BYTES / 3);
        assertThat(hotPage.hasRemaining()).isFalse();
        frame.flip();
        assertThat(Zstd.getFrameContentSize(frame)).isEqualTo(PAGE_BYTES);

        ByteBuffer restored = ByteBuffer.allocateDirect(PAGE_BYTES);
        int read = Zstd.decompress(restored, frame);
        assertThat(read).isEqualTo(PAGE_BYTES);
        assertThat(frame.hasRemaining()).isFalse();
        restored.flip();
        byte[] copy = new byte[PAGE_BYTES];
        restored.get(copy);
        assertThat(Arrays.equals(copy, 0, PAGE_BYTES, heapFile, PAGE_BYTES, 2 * PAGE_BYTES)).isTrue();

        hotPage.rewind();
        ByteBuffer owned = Zstd.compress(hotPage, LEVEL);
        assertThat(owned.isDirect()).isTrue();
        ByteBuffer inflated = Zstd.decompress(owned, PAGE_BYTES);
        assertThat(inflated.remaining()).isEqualTo(PAGE_BYTES);
        hotPage.rewind();
        assertThat(inflated).isEqualTo(hotPage);
    }

    @Test
    void formattedPagesCompressAgainstTheEmptyPageTemplate() {
        byte[] template = new byte[PAGE_BYTES];
        writeHeader(template, 0, -1);
        for (int row = 0; row < (PAGE_BYTES - HEADER_BYTES) / ROW_BYTES; row++) {
            byte[] slot = String.format("%-63s\n", "free|lsn=0000000000|xmin=0|xmax=0")
                    .getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(slot, 0, template, HEADER_BYTES + row * ROW_BYTES, ROW_BYTES);
        }

        byte[] formatted = template.clone();
        writeHeader(formatted, 0, 41);
        byte[] firstRow = String.format("%-63s\n", "row=41-000|lsn=0000004096|xmin=9|sku=AX-1")
                .getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(firstRow, 0, formatted, HEADER_BYTES, ROW_BYTES);

        byte[] withTemplate = Zstd.compressUsingDict(formatted, template, LEVEL);
        byte[] withoutTemplate = Zstd.compress(formatted, LEVEL);
        assertThat(withTemplate.length).isLessThan(withoutTemplate.length);
        assertThat(Zstd.getFrameContentSize(withTemplate)).isEqualTo(PAGE_BYTES);

        byte[] restored = Zstd.decompress(withTemplate, template, PAGE_BYTES);
        assertThat(restored).isEqualTo(formatted);
        assertThat(Zstd.decompress(withoutTemplate, PAGE_BYTES)).isEqualTo(formatted);
    }

    private static byte[] heapFile(int pages) {
        Random random = new Random(42);
        String[] skus = {"AX-1", "AX-2", "BR-7", "CQ-3", "DL-9"};
        byte[] file = new byte[pages * PAGE_BYTES];
        for (int page = 0; page < pages; page++) {
            int base = page * PAGE_BYTES;
            writeHeader(file, base, page);
            for (int row = 0; row < (PAGE_BYTES - HEADER_BYTES) / ROW_BYTES; row++) {
                String text = String.format("row=%d-%03d|lsn=%010d|qty=%d|sku=%s", page, row,
                        page * 4096L + row * 64L, random.nextInt(500), skus[random.nextInt(skus.length)]);
                byte[] encoded = String.format("%-63s\n", text).getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(encoded, 0, file, base + HEADER_BYTES + row * ROW_BYTES, ROW_BYTES);
            }
        }
        return file;
    }

    private static void writeHeader(byte[] target, int offset, int pageNo) {
        byte[] header = String.format("%-127s\n", "PAGE|v3|table=orders|page=" + pageNo + "|rows="
                + (PAGE_BYTES - HEADER_BYTES) / ROW_BYTES).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, target, offset, HEADER_BYTES);
    }
}
