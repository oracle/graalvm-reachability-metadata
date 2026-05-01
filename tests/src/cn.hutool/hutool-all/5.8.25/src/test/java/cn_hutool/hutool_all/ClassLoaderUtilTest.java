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
    @TempDir
    Path temporaryDirectory;

    @Test
    public void loadsArrayClassNamesInSupportedFormats() {
        Class<?> canonicalArray = ClassLoaderUtil.loadClass("java.lang.String[]", false);
        Class<?> jvmArray = ClassLoaderUtil.loadClass("[Ljava.lang.String;", false);
        Class<?> nestedJvmArray = ClassLoaderUtil.loadClass("[[Ljava.lang.String;", false);

        assertThat(canonicalArray).isSameAs(String[].class);
        assertThat(jvmArray).isSameAs(String[].class);
        assertThat(nestedJvmArray).isSameAs(String[][].class);
    }

    @Test
    public void loadsInnerClassWhenCanonicalNameIsProvided() {
        Class<?> threadState = ClassLoaderUtil.loadClass("java.lang.Thread.State", false);

        assertThat(threadState).isSameAs(Thread.State.class);
    }

    @Test
    public void delegatesFileBasedClassLoadingToJarClassLoader() {
        try {
            Class<?> stringClass = ClassLoaderUtil.loadClass(temporaryDirectory.toFile(), "java.lang.String");

            assertThat(stringClass).isSameAs(String.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }
}
