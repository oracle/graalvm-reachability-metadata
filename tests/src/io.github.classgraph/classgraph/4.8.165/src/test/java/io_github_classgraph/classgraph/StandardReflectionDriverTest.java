/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ModulePathInfo;
import io.github.classgraph.ScanResult;
import org.junit.jupiter.api.Test;

public class StandardReflectionDriverTest {
    @Test
    void readsModulePathAndEnumConstantsFromPackageScan() {
        ModulePathInfo modulePathInfo = new ClassGraph().getModulePathInfo();

        assertThat(modulePathInfo).isNotNull();

        try (ScanResult scanResult = new ClassGraph().disableModuleScanning().enableFieldInfo()
                .acceptPackages(StandardReflectionDriverTest.class.getPackageName()).scan()) {
            assertThat(scanResult.getAllClasses()).isNotEmpty();

            ClassInfo enumClassInfo = scanResult.getClassInfo(ScanTarget.class.getName());
            assertThat(enumClassInfo).isNotNull();
            assertThat(enumClassInfo.getEnumConstantObjects()).containsExactly(ScanTarget.FIRST, ScanTarget.SECOND);
        }
    }

    public enum ScanTarget {
        FIRST,
        SECOND
    }
}
