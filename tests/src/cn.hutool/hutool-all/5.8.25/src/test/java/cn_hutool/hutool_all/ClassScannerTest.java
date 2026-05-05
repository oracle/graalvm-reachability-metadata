/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.lang.ClassScanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassScannerTest extends ClassScanner {

    @Test
    void loadsClassWithConfiguredClassLoader() {
        setClassLoader(ClassScannerTest.class.getClassLoader());

        Class<?> loadedClass = loadClass(ClassScanner.class.getName());

        assertThat(loadedClass).isSameAs(ClassScanner.class);
        assertThat(getClassesOfLoadError()).isEmpty();
    }
}
