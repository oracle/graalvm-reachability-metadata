/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import org.hsqldb.lib.FileUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileUtilTest {
    @Test
    void findsBundledSqlStateMessages() {
        FileUtil files = FileUtil.getFileUtil();
        assertTrue(files.exists("/org/hsqldb/resources/sql-state-messages.properties", true, FileUtil.class));
        assertFalse(files.exists("/org/hsqldb/resources/absent-messages.properties", true, FileUtil.class));
    }
}
