/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.io.VFS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VFSTest {
    @Test
    void listsBundledConfigurationResources() throws Exception {
        List<String> resources = VFS.getInstance().list("org/apache/ibatis/builder/xml");
        assertTrue(resources.contains("org/apache/ibatis/builder/xml/mybatis-3-config.dtd"));
        assertTrue(resources.contains("org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd"));
    }
}
