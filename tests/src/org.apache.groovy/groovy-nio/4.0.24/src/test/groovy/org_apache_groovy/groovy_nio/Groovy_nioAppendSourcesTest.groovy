/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_nio

import org.junit.jupiter.api.Test

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

public class Groovy_nioAppendSourcesTest {
    @Test
    void appendsTextFromReaderWriterAndInputStreamSources() {
        Path tempDir = Files.createTempDirectory('groovy-nio-append')
        def file = tempDir.resolve('combined.txt')
        try {
            Files.writeString(file, 'start', StandardCharsets.UTF_8)

            StringReader readerSource = new StringReader('-reader')
            file.append(readerSource, 'UTF-8')

            StringWriter writerSource = new StringWriter()
            writerSource.write('-writer')
            file.append(writerSource, 'UTF-8')

            InputStream inputSource = new ByteArrayInputStream('-input'.getBytes(StandardCharsets.UTF_8))
            try {
                file.append(inputSource)
            } finally {
                inputSource.close()
            }

            assert Files.readString(file, StandardCharsets.UTF_8) == 'start-reader-writer-input'
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(tempDir)
        }
    }

    @Test
    void leftShiftCopiesInputStreamBytesToPath() {
        Path tempDir = Files.createTempDirectory('groovy-nio-left-shift')
        def file = tempDir.resolve('bytes.bin')
        try {
            byte[] expected = 'stream-bytes'.getBytes(StandardCharsets.UTF_8)
            InputStream inputSource = new ByteArrayInputStream(expected)

            def returned
            try {
                returned = file << inputSource
            } finally {
                inputSource.close()
            }

            assert returned == file
            assert Files.readAllBytes(file).toList() == expected.toList()
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(tempDir)
        }
    }
}
