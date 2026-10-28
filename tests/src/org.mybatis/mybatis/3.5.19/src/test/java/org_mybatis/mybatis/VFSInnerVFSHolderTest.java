/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.nio.file.Path;

import org.apache.ibatis.io.VFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VFSInnerVFSHolderTest {
    @Test
    void selectsWorkingFilesystemAdapterForResourceListing(@TempDir Path directory) throws Exception {
        try (ArchivedResources resources = new ArchivedResources(directory.resolve("configuration.jar"),
                "org/apache/ibatis/builder/xml", "mybatis-3-config.dtd")) {
            VFS vfs = VFS.getInstance();
            assertTrue(vfs.isValid());
            assertTrue(vfs.list("org/apache/ibatis/builder/xml").contains(
                    "org/apache/ibatis/builder/xml/mybatis-3-config.dtd"));
        }
    }
}
