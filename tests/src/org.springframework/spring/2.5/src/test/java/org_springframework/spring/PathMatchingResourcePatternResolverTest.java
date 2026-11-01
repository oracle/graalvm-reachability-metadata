/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.junit.jupiter.api.Assertions.*;

public class PathMatchingResourcePatternResolverTest {
    @Test
    void enumeratesClasspathResourcesAndReadsMappings() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:META-INF/spring.handlers");
        assertTrue(resources.length > 0);
        boolean found = false;
        for (Resource resource : resources) {
            Properties mappings = new Properties();
            try (InputStream stream = resource.getInputStream()) {
                mappings.load(stream);
            }
            found |= mappings.containsKey("http://www.springframework.org/schema/beans");
            found |= mappings.containsKey("http://www.springframework.org/schema/util");
        }
        assertTrue(found);
    }
}
