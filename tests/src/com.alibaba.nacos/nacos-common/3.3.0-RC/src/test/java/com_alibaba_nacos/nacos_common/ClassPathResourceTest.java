/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.packagescan.resource.ClassPathResource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassPathResourceTest {
    private static final String RESOURCE = "com_alibaba_nacos/nacos_common/settings.properties";

    @Test
    void readsAResourceRelativeToAClass() throws Exception {
        verify(new ClassPathResource("settings.properties", ClassPathResourceTest.class));
    }

    @Test
    void readsAResourceThroughAnExplicitClassLoader() throws Exception {
        verify(new ClassPathResource("/" + RESOURCE, getClass().getClassLoader()));
    }

    @Test
    void readsAResourceThroughTheSystemClassLoader() throws Exception {
        verify(new ClassPathResource(RESOURCE, (Class<?>) null));
    }

    private void verify(ClassPathResource resource) throws Exception {
        assertTrue(resource.exists());
        assertNotNull(resource.getUrl());
        assertEquals("settings.properties", resource.getFilename());
        try (InputStream stream = resource.getInputStream()) {
            assertEquals("mode=cluster\n", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
