/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import com.alibaba.nacos.common.utils.ResourceUtils;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class ResourceUtilsTest {
    @Test
    void resolvesAndReadsLibraryVersionResource() throws Exception {
        ClassLoader loader = ResourceUtils.class.getClassLoader();
        assertNotNull(ResourceUtils.getResourceUrl("classpath:nacos-version.txt"));
        assertNotNull(ResourceUtils.getResourceUrl(loader, "nacos-version.txt"));
        assertNotNull(ResourceUtils.getResourceUrl(null, "nacos-version.txt"));
        for (ClassLoader candidate : new ClassLoader[] {loader, null}) {
            try (InputStream input = ResourceUtils.getResourceAsStream(candidate, "nacos-version.txt")) {
                Properties properties = new Properties();
                properties.load(input);
                assertFalse(properties.getProperty("version").isBlank());
            }
        }
    }
}
