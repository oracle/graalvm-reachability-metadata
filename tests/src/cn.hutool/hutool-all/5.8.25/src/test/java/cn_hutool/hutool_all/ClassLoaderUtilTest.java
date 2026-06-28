/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.util.ClassLoaderUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderUtilTest {
    @Test
    void loadsArrayNamesInSupportedSourceAndJvmDescriptorFormats() {
        ClassLoader classLoader = ClassLoaderUtil.getClassLoader();

        Class<?> sourceStyleArray = ClassLoaderUtil.loadClass(
                "java.lang.String[]", classLoader, false);
        Class<?> objectDescriptorArray = ClassLoaderUtil.loadClass(
                "[Ljava.lang.String;", classLoader, false);
        Class<?> internalDescriptorArray = ClassLoaderUtil.loadClass(
                "[[I", classLoader, false);

        assertThat(sourceStyleArray).isEqualTo(String[].class);
        assertThat(objectDescriptorArray).isEqualTo(String[].class);
        assertThat(internalDescriptorArray).isEqualTo(int[][].class);
    }

    @Test
    void convertsCanonicalInnerClassNameBeforeLoading() {
        Class<?> threadState = ClassLoaderUtil.loadClass("java.lang.Thread.State", false);

        assertThat(threadState).isEqualTo(Thread.State.class);
    }

    @Test
    void loadsParentVisibleClassThroughJarClassLoaderApi(@TempDir Path classPathRoot) {
        Class<?> stringClass = ClassLoaderUtil.loadClass(
                classPathRoot.toFile(), "java.lang.String");

        assertThat(stringClass).isEqualTo(String.class);
    }
}
