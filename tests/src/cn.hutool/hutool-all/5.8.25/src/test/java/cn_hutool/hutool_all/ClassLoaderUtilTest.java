/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.util.ClassLoaderUtil;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderUtilTest {

    @Test
    void loadsArrayClassNamesInSupportedHutoolFormats() {
        assertThat(ClassLoaderUtil.loadClass(String.class.getName() + "[]", getClass().getClassLoader(), false))
                .isSameAs(String[].class);
        assertThat(ClassLoaderUtil.loadClass("[Ljava.lang.String;", getClass().getClassLoader(), false))
                .isSameAs(String[].class);
        assertThat(ClassLoaderUtil.loadClass("[[Ljava.lang.String;", getClass().getClassLoader(), false))
                .isSameAs(String[][].class);
        assertThat(ClassLoaderUtil.loadClass("[[I", getClass().getClassLoader(), false))
                .isSameAs(int[][].class);
    }

    @Test
    void resolvesCanonicalInnerClassNameByTryingBinaryName() {
        Class<?> loadedClass = ClassLoaderUtil.loadClass("java.lang.Thread.State", getClass().getClassLoader(), false);

        assertThat(loadedClass).isSameAs(Thread.State.class);
    }

    @Test
    void loadsClassThroughJarClassLoaderLoadClass(@TempDir Path tempDir) {
        try {
            Class<?> loadedClass = ClassLoaderUtil.loadClass(tempDir.toFile(), ClassLoaderUtilTest.class.getName());

            assertThat(loadedClass).isSameAs(ClassLoaderUtilTest.class);
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error);
        }
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
