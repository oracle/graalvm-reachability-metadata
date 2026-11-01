/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.junit.jupiter.api.Assertions.*;

public class PropertiesLoaderUtilsTest {
    @Test
    void loadsSpringNamespaceMappingsFromAllClasspathLocations() throws Exception {
        Properties mappings = PropertiesLoaderUtils.loadAllProperties("META-INF/spring.handlers");
        assertEquals("org.springframework.beans.factory.xml.UtilNamespaceHandler",
                mappings.getProperty("http://www.springframework.org/schema/util"));
    }
}
