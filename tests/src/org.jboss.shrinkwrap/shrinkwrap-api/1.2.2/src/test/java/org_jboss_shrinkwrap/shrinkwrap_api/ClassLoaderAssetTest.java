/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.junit.jupiter.api.Test;

public class ClassLoaderAssetTest {
    private static final String RESOURCE_NAME = "org_jboss_shrinkwrap/shrinkwrap_api/class-loader-asset.txt";
    private static final String RESOURCE_CONTENT = "class loader asset content";

    @Test
    public void opensResourceFromExplicitClassLoader() throws IOException {
        ClassLoader classLoader = ClassLoaderAssetTest.class.getClassLoader();

        assertNotNull(classLoader.getResource(RESOURCE_NAME));
        ClassLoaderAsset asset = new ClassLoaderAsset(RESOURCE_NAME, classLoader);

        assertEquals(RESOURCE_NAME, asset.getSource());
        try (InputStream stream = asset.openStream()) {
            assertEquals(RESOURCE_CONTENT, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
