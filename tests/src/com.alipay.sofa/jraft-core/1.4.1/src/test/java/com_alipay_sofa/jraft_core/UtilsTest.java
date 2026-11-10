/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alipay_sofa.jraft_core;

import com.alipay.sofa.jraft.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UtilsTest {
    @TempDir
    Path directory;

    @Test
    void unmapsAndMovesPersistedMappedData() throws Exception {
        Path source = directory.resolve("mapped-data");
        Path target = directory.resolve("committed-data");
        byte[] expected = "committed raft payload".getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.CREATE,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, expected.length);
            try {
                buffer.put(expected);
                buffer.force();
            } finally {
                Utils.unmap(buffer);
            }
        }
        assertTrue(Utils.atomicMoveFile(source.toFile(), target.toFile(), true));
        assertFalse(Files.exists(source));
        assertArrayEquals(expected, Files.readAllBytes(target));
        Files.delete(target);
        assertFalse(Files.exists(target));
    }
}
