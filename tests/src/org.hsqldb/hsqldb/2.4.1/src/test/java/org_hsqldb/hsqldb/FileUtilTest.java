/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hsqldb.lib.FileUtil;
import org.junit.jupiter.api.Test;

public class FileUtilTest {
    private static final String EXISTING_RESOURCE = "/lobstoreinjarcoverage.lobs";
    private static final String MISSING_RESOURCE = "/missing-hsqldb-file-util-resource.dat";

    @Test
    public void checksResourceExistenceThroughClassLookup() {
        FileUtil fileUtil = FileUtil.getFileUtil();

        assertTrue(fileUtil.exists(EXISTING_RESOURCE, true, FileUtilTest.class));
        assertFalse(fileUtil.exists(MISSING_RESOURCE, true, FileUtilTest.class));
    }
}
