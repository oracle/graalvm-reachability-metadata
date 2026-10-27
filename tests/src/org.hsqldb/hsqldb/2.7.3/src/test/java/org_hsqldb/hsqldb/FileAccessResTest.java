/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.hsqldb.lib.FileAccess;
import org.hsqldb.lib.FileUtil;
import org.junit.jupiter.api.Test;

public class FileAccessResTest {
    private static final String RESOURCE = "org/hsqldb/resources/information-schema.sql";

    @Test
    void readsPackagedResourcesUsingClassAndContextClassLoaderLookup() throws IOException {
        FileAccess fileAccess = FileUtil.getFileAccess(true);

        assertReadable(fileAccess, "/" + RESOURCE);
        assertReadable(fileAccess, RESOURCE);
    }

    private static void assertReadable(FileAccess fileAccess, String resource) throws IOException {
        assertThat(fileAccess.isStreamElement(resource)).isTrue();

        try (InputStream inputStream = fileAccess.openInputStreamElement(resource)) {
            assertThat(inputStream.read()).isGreaterThanOrEqualTo(0);
        }
    }
}
