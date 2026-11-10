/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.ibatis.io.JBoss6VFS;
import org.apache.ibatis.io.VFS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VFSTest {
    @Test
    void listsNestedFilesThroughTheJBossAdapter(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("settings.xml"), "<settings/>");
        Path nested = Files.createDirectory(directory.resolve("mappers"));
        Files.writeString(nested.resolve("people.xml"), "<mapper/>");

        JBoss6VFS vfs = new JBoss6VFS();
        assertTrue(vfs.isValid());
        List<String> resources = vfs.list(directory.toUri().toURL(), "configuration");
        assertTrue(resources.contains("configuration/settings.xml"));
        assertTrue(resources.contains("configuration/mappers/people.xml"));
    }

    @Test
    void listsBundledConfigurationResources(@TempDir Path directory) throws Exception {
        try (ArchivedResources archive = new ArchivedResources(directory.resolve("configuration.jar"),
                "org/apache/ibatis/builder/xml", "mybatis-3-config.dtd", "mybatis-3-mapper.dtd")) {
            List<String> resources = VFS.getInstance().list("org/apache/ibatis/builder/xml");
            assertTrue(resources.contains("org/apache/ibatis/builder/xml/mybatis-3-config.dtd"));
            assertTrue(resources.contains("org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd"));
        }
    }
}
