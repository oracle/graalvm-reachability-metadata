/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_nio

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals

public class WriteAppendExtensionsTest {
    @Test
    void writesAndAppendsTextWithExplicitCharset(@TempDir Path tempDir) {
        Path file = tempDir.resolve('write-append.txt')

        file.write('alpha', 'UTF-8')
        file.append('\nbeta', 'UTF-8')

        assertEquals(['alpha', 'beta'], file.readLines('UTF-8'))
    }
}
