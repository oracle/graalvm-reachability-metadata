/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene.lucene_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.apache.lucene.util.IOUtils;
import org.junit.jupiter.api.Test;

public class IOUtilsTest {
    @Test
    public void opensPackageResourceWithDecodingReader() throws IOException {
        try (Reader reader = IOUtils.getDecodingReader(
                IOUtilsTest.class,
                "IOUtilsTestResource.txt",
                StandardCharsets.UTF_8)) {
            char[] buffer = new char[64];
            int readCharacters = reader.read(buffer);

            assertThat(new String(buffer, 0, readCharacters)).isEqualTo("resource loaded through IOUtils\n");
            assertThat(reader.read()).isEqualTo(-1);
        }
    }
}
