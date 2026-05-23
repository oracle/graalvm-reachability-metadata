/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import org.hsqldb.lib.FileUtil;
import org.junit.jupiter.api.Test;

public class FileUtilTest {
    @Test
    void resourceExistenceChecksClassResourcePath() {
        boolean exists = FileUtil.getFileUtil().exists(
                "/org_hsqldb/hsqldb/file-util-resource.txt",
                true,
                FileUtilTest.class);

        assertThat(exists).isTrue();
    }
}
