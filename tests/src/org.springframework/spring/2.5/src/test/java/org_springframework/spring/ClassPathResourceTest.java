/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.support.SQLErrorCodesFactory;

import static org.junit.jupiter.api.Assertions.*;

public class ClassPathResourceTest {
    @Test
    void loadsResourcesRelativeToClassAndClassLoader() throws Exception {
        ClassPathResource[] resources = {
                new ClassPathResource("sql-error-codes.xml", SQLErrorCodesFactory.class),
                new ClassPathResource("org/springframework/jdbc/support/sql-error-codes.xml", getClass().getClassLoader())};
        for (ClassPathResource resource : resources) {
            assertNotNull(resource.getURL());
            try (InputStream stream = resource.getInputStream()) {
                assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("databaseProductName"));
            }
        }
    }
}
