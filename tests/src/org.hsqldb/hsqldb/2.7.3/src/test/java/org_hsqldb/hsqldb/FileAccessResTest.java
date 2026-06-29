/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.io.FileNotFoundException;

import org.hsqldb.lib.FileAccess;
import org.hsqldb.lib.FileAccessRes;
import org.hsqldb.lib.FileUtil;
import org.junit.jupiter.api.Test;

public class FileAccessResTest {
    private static final String MISSING_RESOURCE_NAME = "org_hsqldb/hsqldb/missing-file-access-res-resource.txt";
    private static final String MISSING_ABSOLUTE_RESOURCE_NAME = "/" + MISSING_RESOURCE_NAME;

    @Test
    void missingRelativeResourceChecksClassAndContextClassLoader() {
        FileAccessRes fileAccess = new FileAccessRes();
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();

        currentThread.setContextClassLoader(FileAccessResTest.class.getClassLoader());
        try {
            assertThat(fileAccess.isStreamElement(MISSING_RESOURCE_NAME)).isFalse();
            assertThatIOException()
                    .isThrownBy(() -> fileAccess.openInputStreamElement(MISSING_RESOURCE_NAME))
                    .isInstanceOf(FileNotFoundException.class)
                    .withMessage(MISSING_RESOURCE_NAME);
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    @Test
    void missingAbsoluteResourceChecksOwningClass() {
        FileAccessRes fileAccess = new FileAccessRes();

        assertThat(fileAccess.isStreamElement(MISSING_ABSOLUTE_RESOURCE_NAME)).isFalse();
        assertThatIOException()
                .isThrownBy(() -> fileAccess.openInputStreamElement(MISSING_ABSOLUTE_RESOURCE_NAME))
                .isInstanceOf(FileNotFoundException.class)
                .withMessage(MISSING_ABSOLUTE_RESOURCE_NAME);
    }

    @Test
    void fileUtilResourceAccessorUsesFileAccessRes() {
        FileAccess fileAccess = FileUtil.getFileAccess(true);

        assertThat(fileAccess).isInstanceOf(FileAccessRes.class);
        assertThat(fileAccess.isStreamElement(MISSING_RESOURCE_NAME)).isFalse();
        assertThatIOException()
                .isThrownBy(() -> fileAccess.openInputStreamElement(MISSING_RESOURCE_NAME))
                .isInstanceOf(FileNotFoundException.class)
                .withMessage(MISSING_RESOURCE_NAME);
    }
}
