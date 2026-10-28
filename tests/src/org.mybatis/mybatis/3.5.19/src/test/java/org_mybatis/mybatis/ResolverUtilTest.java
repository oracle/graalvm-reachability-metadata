/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.nio.file.Path;

import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResolverUtilTest {
    @Test
    void discoversSqlSourceImplementationInBundledPackage(@TempDir Path directory) throws Exception {
        try (ArchivedResources resources = new ArchivedResources(directory.resolve("scripting.jar"),
                "org/apache/ibatis/scripting/defaults", "RawSqlSource.class", "DefaultParameterHandler.class")) {
            ResolverUtil<SqlSource> resolver = new ResolverUtil<>();
            resolver.findImplementations(SqlSource.class, "org.apache.ibatis.scripting.defaults");
            assertTrue(resolver.getClasses().contains(RawSqlSource.class));
            assertEquals(1, resolver.getClasses().size());
        }
    }
}
