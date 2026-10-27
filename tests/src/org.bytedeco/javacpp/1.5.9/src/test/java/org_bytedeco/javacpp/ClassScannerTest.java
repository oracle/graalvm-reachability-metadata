/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.tools.Builder;

public class ClassScannerTest {

    @Platform(define = "JAVACPP_CLASS_SCANNER_TEST")
    public static class ScannedBinding {}

    @Test
    public void classesOrPackagesFindsNamedBindingClass() throws Exception {
        String oldLoadLibraries = System.getProperty("org.bytedeco.javacpp.loadlibraries");
        try {
            Builder builder = new Builder().generate(false).compile(false);

            builder.classesOrPackages(ScannedBinding.class.getName());
            File[] generated = builder.build();

            assertThat(generated).isNotNull().isEmpty();
        } finally {
            restoreProperty("org.bytedeco.javacpp.loadlibraries", oldLoadLibraries);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
