/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_googlecode_xmemcached.xmemcached;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.code.yanf4j.util.ResourcesUtils;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class ResourcesUtilsTest {
    private static final String RESOURCE = "com_googlecode_xmemcached/xmemcached/client.properties";
    private static final String MISSING_RESOURCE = "com_googlecode_xmemcached/xmemcached/missing.properties";

    @Test
    void resolvesResourcesWithDefaultAndExplicitClassLoaders() throws Exception {
        ClassLoader loader = ResourcesUtilsTest.class.getClassLoader();

        URL defaultUrl = ResourcesUtils.getResourceURL(RESOURCE);
        URL explicitUrl = ResourcesUtils.getResourceURL(loader, RESOURCE);

        assertThat(defaultUrl).isNotNull();
        assertThat(explicitUrl).isNotNull();
        try (InputStream defaultStream = ResourcesUtils.getResourceAsStream(RESOURCE);
                InputStream explicitStream = ResourcesUtils.getResourceAsStream(loader, RESOURCE)) {
            assertThat(new String(defaultStream.readAllBytes(), StandardCharsets.UTF_8)).contains("mode=test");
            assertThat(new String(explicitStream.readAllBytes(), StandardCharsets.UTF_8)).contains("mode=test");
        }
    }

    @Test
    void reportsMissingResourcesAfterTryingSystemClassLoader() {
        ClassLoader loader = ResourcesUtilsTest.class.getClassLoader();

        assertThatThrownBy(() -> ResourcesUtils.getResourceURL(MISSING_RESOURCE))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining(MISSING_RESOURCE);
        assertThatThrownBy(() -> ResourcesUtils.getResourceURL(loader, MISSING_RESOURCE))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining(MISSING_RESOURCE);
        assertThatThrownBy(() -> ResourcesUtils.getResourceAsStream(MISSING_RESOURCE))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining(MISSING_RESOURCE);
        assertThatThrownBy(() -> ResourcesUtils.getResourceAsStream(loader, MISSING_RESOURCE))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining(MISSING_RESOURCE);
    }
}
