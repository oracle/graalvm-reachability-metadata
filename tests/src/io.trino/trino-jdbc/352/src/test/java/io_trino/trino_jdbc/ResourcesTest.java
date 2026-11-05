/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.io.Resources;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourcesTest {
    @Test
    void readsAbsoluteAndPackageRelativeClasspathResources() throws Exception {
        assertEquals("inventory=ready\n", Resources.toString(Resources.getResource("io_trino/trino_jdbc/sample.txt"), StandardCharsets.UTF_8));
        assertEquals("inventory=ready\n", Resources.toString(Resources.getResource(ResourcesTest.class, "sample.txt"), StandardCharsets.UTF_8));
    }
}
