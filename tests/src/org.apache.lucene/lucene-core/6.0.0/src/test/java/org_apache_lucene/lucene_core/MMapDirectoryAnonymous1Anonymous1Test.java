/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import java.io.IOException;
import java.lang.reflect.InaccessibleObjectException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.RandomAccessInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class MMapDirectoryAnonymous1Anonymous1Test {
    private static final String INDEX_FILE_NAME = "mapped-input.bin";

    @TempDir
    Path temporaryDirectory;

    @Test
    void closingMappedInputUsesCleanerWhenUnmapIsEnabled() throws IOException {
        byte[] content = new byte[] {10, 20, 30, 40, 50, 60, 70, 80};
        Files.write(temporaryDirectory.resolve(INDEX_FILE_NAME), content);

        MMapDirectory directory = new AlwaysUnmappingMMapDirectory(temporaryDirectory);
        try {
            IndexInput input = directory.openInput(INDEX_FILE_NAME, IOContext.DEFAULT);
            try {
                RandomAccessInput randomAccessInput = (RandomAccessInput) input;

                assertThat(input.length()).isEqualTo(content.length);
                assertThat(input.readByte()).isEqualTo(content[0]);
                assertThat(randomAccessInput.readLong(0)).isEqualTo(0x0A141E28323C4650L);
            } finally {
                closeMappedInput(input);
            }
        } finally {
            directory.close();
        }
    }

    private static void closeMappedInput(IndexInput input) throws IOException {
        try {
            input.close();
        } catch (IOException e) {
            assertThat(e).hasMessageContaining("Unable to unmap the mapped buffer");
            assertThat(e.getCause())
                    .isInstanceOfAny(IllegalAccessException.class, NoSuchMethodException.class);
        } catch (InaccessibleObjectException e) {
            assertThat(e).hasMessageContaining("java.nio");
        }
    }

    private static final class AlwaysUnmappingMMapDirectory extends MMapDirectory {
        private AlwaysUnmappingMMapDirectory(Path path) throws IOException {
            super(path);
        }

        @Override
        public boolean getUseUnmap() {
            return true;
        }
    }
}
