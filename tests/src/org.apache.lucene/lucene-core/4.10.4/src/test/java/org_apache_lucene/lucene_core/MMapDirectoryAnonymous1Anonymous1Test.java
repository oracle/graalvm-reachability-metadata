/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.MMapDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MMapDirectoryAnonymous1Anonymous1Test {
    @TempDir
    Path temporaryDirectory;

    @Test
    public void closesMappedInputWithUnmapCleaner() throws IOException {
        Path indexFile = temporaryDirectory.resolve("mapped-segments.dat");
        byte[] expectedBytes = "lucene mmap directory cleaner coverage".getBytes(StandardCharsets.UTF_8);
        Files.write(indexFile, expectedBytes);

        File directoryPath = temporaryDirectory.toFile();
        try (MMapDirectory directory = new AlwaysUnmappingMMapDirectory(directoryPath, 8)) {
            assertThat(directory.getUseUnmap()).isTrue();

            try (IndexInput input = directory.openInput(indexFile.getFileName().toString(), IOContext.READONCE)) {
                byte[] actualBytes = new byte[expectedBytes.length];
                input.readBytes(actualBytes, 0, actualBytes.length);

                assertThat(actualBytes).containsExactly(expectedBytes);
                assertThat(input.getFilePointer()).isEqualTo(expectedBytes.length);
            }
        }
    }

    private static final class AlwaysUnmappingMMapDirectory extends MMapDirectory {
        private AlwaysUnmappingMMapDirectory(File path, int maxChunkSize) throws IOException {
            super(path, null, maxChunkSize);
        }

        @Override
        public boolean getUseUnmap() {
            return true;
        }
    }
}
