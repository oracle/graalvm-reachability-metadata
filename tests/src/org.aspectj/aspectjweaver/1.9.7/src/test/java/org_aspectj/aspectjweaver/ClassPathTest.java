/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.aspectj.apache.bcel.util.ClassPath;
import org.junit.jupiter.api.Test;

public class ClassPathTest {
    @Test
    void checksClassLoaderResourcesBeforeSearchingConfiguredClassPath() {
        ClassPath emptyClassPath = new ClassPath("");
        String missingResourceName = ClassPathTest.class.getName().replace('.', '/') + "Missing";

        assertThatThrownBy(() -> emptyClassPath.getInputStream(missingResourceName, ".class"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(missingResourceName + ".class");
    }
}
