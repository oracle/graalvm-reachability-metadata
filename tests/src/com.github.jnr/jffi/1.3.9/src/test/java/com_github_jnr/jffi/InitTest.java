/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jnr.jffi;

import com.kenai.jffi.Library;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InitTest {
    @Test
    void reportsLoadFailureWhenOperatingSystemIsUnsupported() {
        String propertyName = "os.name";
        String previousOsName = System.getProperty(propertyName);
        System.setProperty(propertyName, "GraalVM Reachability Metadata Unsupported OS");

        try {
            assertThatThrownBy(Library::getDefault)
                    .isInstanceOf(UnsatisfiedLinkError.class)
                    .hasMessageContaining("cannot determine operating system");
        } finally {
            if (previousOsName == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previousOsName);
            }
        }
    }
}
