/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jdt.ecj;

import javax.tools.JavaCompiler;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;

final class EcjTestSupport {
    private EcjTestSupport() {
    }

    static JavaCompiler newCompiler() {
        ensureJavaHomeProperty();
        return new EclipseCompiler();
    }

    private static void ensureJavaHomeProperty() {
        if (System.getProperty("java.home") != null) {
            return;
        }
        final String javaHome = firstNonBlank(System.getenv("JAVA_HOME"), System.getenv("GRAALVM_HOME"));
        if (javaHome != null) {
            System.setProperty("java.home", javaHome);
        }
    }

    private static String firstNonBlank(String primaryValue, String fallbackValue) {
        if (primaryValue != null && !primaryValue.isBlank()) {
            return primaryValue;
        }
        if (fallbackValue != null && !fallbackValue.isBlank()) {
            return fallbackValue;
        }
        return null;
    }
}
