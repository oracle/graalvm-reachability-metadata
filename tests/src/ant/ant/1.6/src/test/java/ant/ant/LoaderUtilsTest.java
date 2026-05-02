/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;

import org.apache.tools.ant.util.LoaderUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoaderUtilsTest {
    private static final String TEST_RESOURCE = "ant/ant/property-resource.properties";

    @Test
    void findsResourceSourceWithDefaultLoader() {
        File defaultLoaderSource = LoaderUtils.getResourceSource(null, TEST_RESOURCE);
        File explicitLoaderSource = LoaderUtils.getResourceSource(
                LoaderUtilsTest.class.getClassLoader(),
                TEST_RESOURCE);

        assertThat(defaultLoaderSource).isEqualTo(explicitLoaderSource);
        if (defaultLoaderSource != null) {
            assertThat(defaultLoaderSource).exists();
        }
    }

    @Test
    void setsAndRestoresContextClassLoader() {
        ClassLoader originalLoader = LoaderUtils.getContextClassLoader();
        ClassLoader platformLoader = ClassLoader.getPlatformClassLoader();

        try {
            LoaderUtils.setContextClassLoader(platformLoader);

            assertThat(LoaderUtils.isContextLoaderAvailable()).isTrue();
            assertThat(LoaderUtils.getContextClassLoader()).isSameAs(platformLoader);
        } finally {
            LoaderUtils.setContextClassLoader(originalLoader);
        }
    }
}
