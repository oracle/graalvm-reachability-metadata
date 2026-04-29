/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileNotFoundException;

import org.hsqldb.lib.FileUtil.FileAccessRes;
import org.junit.jupiter.api.Test;

public class FileUtilInnerFileAccessResTest {
    private static final String MISSING_RESOURCE = "missing-hsqldb-file-access-resource.dat";

    @Test
    public void checksMissingResourceThroughClassAndContextClassLoader() {
        FileAccessRes access = new FileAccessRes();

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(FileUtilInnerFileAccessResTest.class.getClassLoader());

            assertFalse(access.isStreamElement(MISSING_RESOURCE));
            assertThrows(FileNotFoundException.class, () -> access.openInputStreamElement(MISSING_RESOURCE));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
