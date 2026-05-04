/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_shrinkwrap.shrinkwrap_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.jboss.shrinkwrap.api.asset.ClassLoaderAsset;
import org.junit.jupiter.api.Test;

public class ClassLoaderAssetTest {
    private static final String RESOURCE_NAME = "org_jboss_shrinkwrap/shrinkwrap_api/class-loader-asset.txt";

    @Test
    void constructorFindsResourceAndOpenStreamReadsIt() throws Exception {
        ClassLoader classLoader = ClassLoaderAssetTest.class.getClassLoader();

        ClassLoaderAsset asset = new ClassLoaderAsset(RESOURCE_NAME, classLoader);

        assertThat(asset.getSource()).isEqualTo(RESOURCE_NAME);
        try (InputStream stream = asset.openStream()) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo("resource loaded through ClassLoaderAsset\n");
        }
    }
}
