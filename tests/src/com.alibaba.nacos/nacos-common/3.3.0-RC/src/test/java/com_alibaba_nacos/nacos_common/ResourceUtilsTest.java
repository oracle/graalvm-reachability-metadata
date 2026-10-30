/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.utils.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResourceUtilsTest {
    private static final String RESOURCE = "com_alibaba_nacos/nacos_common/settings.properties";

    @Test
    void resolvesClasspathUrlsAndLoadsProperties() throws Exception {
        URL url = ResourceUtils.getResourceUrl("classpath:" + RESOURCE);
        assertNotNull(url);
        assertEquals(url, ResourceUtils.getResourceUrl(getClass().getClassLoader(), RESOURCE));
        assertEquals(url, ResourceUtils.getResourceUrl(null, RESOURCE));
        assertEquals("cluster", ResourceUtils.getResourceAsProperties(RESOURCE).getProperty("mode"));
    }

    @Test
    void readsStreamsUsingExplicitAndSystemLoaders() throws Exception {
        try (InputStream stream = ResourceUtils.getResourceAsStream(getClass().getClassLoader(), RESOURCE)) {
            assertEquals("mode=cluster\n", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (InputStream stream = ResourceUtils.getResourceAsStream(null, RESOURCE)) {
            assertEquals("mode=cluster\n", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
