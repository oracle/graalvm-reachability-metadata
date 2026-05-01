/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.NoResourceException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ClassPathResourceTest {
    @Test
    public void looksUpResourceRelativeToProvidedClass() {
        assertThatThrownBy(() -> new ClassPathResource("missing-class-relative-resource.txt", ClassPathResourceTest.class))
                .isInstanceOf(NoResourceException.class)
                .hasMessageContaining("missing-class-relative-resource.txt");
    }

    @Test
    public void looksUpResourceWithProvidedClassLoader() {
        String resourcePath = "cn_hutool/hutool_all/missing-class-loader-resource.txt";
        ClassLoader classLoader = ClassLoader.getPlatformClassLoader();

        assertThatThrownBy(() -> new ClassPathResource(resourcePath, classLoader))
                .isInstanceOf(NoResourceException.class)
                .hasMessageContaining(resourcePath);
    }

    @Test
    public void fallsBackWhenThreadContextClassLoaderIsUnavailable() {
        String resourcePath = "cn_hutool/hutool_all/missing-default-resource.txt";
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();

        try {
            currentThread.setContextClassLoader(null);

            assertThatThrownBy(() -> new ClassPathResource(resourcePath))
                    .isInstanceOf(NoResourceException.class)
                    .hasMessageContaining(resourcePath);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }
}
