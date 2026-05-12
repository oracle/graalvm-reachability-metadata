/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_apt;

/// Utilities for configuring compiler-backed tests inside Native Image.
final class NativeCompilerSupport {

    private NativeCompilerSupport() {
    }

    static void ensureJavaHomeProperty() {
        if (System.getProperty("java.home") == null) {
            String javaHome = System.getenv("JAVA_HOME");
            if (javaHome == null || javaHome.isBlank()) {
                throw new IllegalStateException("JAVA_HOME must be set when java.home is unavailable");
            }
            System.setProperty("java.home", javaHome);
        }
    }

}
