/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.hsqldb.lib.FileAccess;
import org.hsqldb.lib.FileUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileUtilInnerFileAccessResTest {
    @Test
    void readsResourcesByAbsoluteAndContextClassLoaderNames() throws Exception {
        FileAccess access = FileUtil.getFileAccess(true);
        for (String name : new String[]{"/org/hsqldb/resources/sql-state-messages.properties",
                "org/hsqldb/resources/sql-state-messages.properties"}) {
            assertTrue(access.isStreamElement(name));
            try (InputStream input = access.openInputStreamElement(name)) {
                String messages = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertTrue(messages.contains("successful completion"));
                assertTrue(messages.contains("23505"));
            }
        }
    }
}
