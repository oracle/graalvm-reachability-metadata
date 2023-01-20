/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_luben.zstd_jni;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictDecompress;
import com.github.luben.zstd.ZstdDirectBufferCompressingStream;
import com.github.luben.zstd.ZstdDirectBufferDecompressingStream;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Zstd_jniTest {
    @Test
    void testCompressAndDecompress() throws IOException {
        Path path = Paths.get("src/test/resources/originTest.txt");
        byte[] bytes = new String(Files.readAllBytes(path)).getBytes(StandardCharsets.UTF_8);
        assertThat(bytes.length).isEqualTo(26);
        byte[] dictBytes = new String(Files.readAllBytes(path)).getBytes(StandardCharsets.UTF_8);
        ZstdDictCompress compressDict = new ZstdDictCompress(dictBytes, 10);
        ZstdDictDecompress decompressDict = new ZstdDictDecompress(dictBytes);
        byte[] compressed = Zstd.compress(bytes, compressDict);
        assertThat(compressed.length).isEqualTo(15);
        byte[] decompressed = Zstd.decompress(compressed, decompressDict, 1000000);
        assertThat(decompressed.length).isEqualTo(26);
    }

    @Test
    void testCompressFile() throws IOException {
        File file = new File("src/test/resources/originTest.txt");
        File outFile = new File("src/test/resources/originTest.txt.zs");
        long numBytes = 0L;
        ByteBuffer inBuffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);
        ByteBuffer compressedBuffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);
        try (RandomAccessFile inRaFile = new RandomAccessFile(file, "r");
             RandomAccessFile outRaFile = new RandomAccessFile(outFile, "rw");
             FileChannel inChannel = inRaFile.getChannel();
             FileChannel outChannel = outRaFile.getChannel()) {
            inBuffer.clear();
            while (inChannel.read(inBuffer) > 0) {
                inBuffer.flip();
                compressedBuffer.clear();
                long compressedSize = Zstd.compressDirectByteBuffer(compressedBuffer, 0, compressedBuffer.capacity(), inBuffer, 0, inBuffer.limit(), 10);
                numBytes = numBytes + compressedSize;
                compressedBuffer.position((int) compressedSize);
                compressedBuffer.flip();
                outChannel.write(compressedBuffer);
                inBuffer.clear();
            }
        }
        assertThat(numBytes).isEqualTo(35);
        assertDoesNotThrow(outFile::delete);
    }

    @Test
    void testDecompressFileByZstdIOStream() {
        File file = new File("src/test/resources/compressTest.zs");
        File out = new File("src/test/resources/DecompressTest.txt");
        byte[] buffer = new byte[1024 * 1024 * 8];
        try (FileOutputStream fo = new FileOutputStream(out);
             ZstdOutputStream zos = new ZstdOutputStream(fo);
             FileInputStream fi = new FileInputStream(file.getPath());
             ZstdInputStream zis = new ZstdInputStream(fi)) {
            while (true) {
                int count = zis.read(buffer, 0, buffer.length);
                if (count == -1) {
                    break;
                }
                zos.write(buffer, 0, count);
            }
            zos.flush();
        } catch (IOException ignore) {
        }
        assertDoesNotThrow(out::delete);
    }

    @Test
    void testDirectBufferDecompressingStream() throws IOException {
        byte[] input = Files.readAllBytes(Paths.get("src/test/resources/originTest.txt"));
        final int size = input.length;
        ByteBuffer os = ByteBuffer.allocateDirect((int) Zstd.compressBound(size));
        final ByteBuffer ib = ByteBuffer.allocateDirect(size);
        ib.put(input);
        final ZstdDirectBufferCompressingStream osw = new ZstdDirectBufferCompressingStream(os, 9);
        ib.flip();
        osw.compress(ib);
        osw.close();
        os.flip();
        final byte[] bytes = new byte[os.limit()];
        os.get(bytes);
        os.rewind();
        final ZstdDirectBufferDecompressingStream zis = new ZstdDirectBufferDecompressingStream(os);
        final byte[] output = new byte[size];
        final ByteBuffer block = ByteBuffer.allocateDirect(128 * 1024);
        int offset = 0;
        while (zis.hasRemaining()) {
            block.clear();
            final int read = zis.read(block);
            block.flip();
            block.get(output, offset, read);
            offset = offset + read;
        }
        zis.close();
        String s = "[97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122]";
        assertThat(Arrays.toString(input)).isEqualTo(s);
        assertThat(Arrays.toString(output)).isEqualTo(s);
    }

}
