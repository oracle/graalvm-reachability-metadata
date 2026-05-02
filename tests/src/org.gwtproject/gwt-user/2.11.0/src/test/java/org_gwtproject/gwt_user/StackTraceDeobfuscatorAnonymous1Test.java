/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.core.server.StackTraceDeobfuscator;

import org.junit.jupiter.api.Test;

public class StackTraceDeobfuscatorAnonymous1Test {
    private static final String RESOURCE_DIRECTORY = "org_gwtproject/gwt_user/stacktracedeobfuscator";
    private static final String STRONG_NAME = "stacktrace_deobfuscator_coverage";

    @Test
    void loadsSymbolMapFromClasspathResource() {
        StackTraceDeobfuscator deobfuscator = StackTraceDeobfuscator.fromResource(RESOURCE_DIRECTORY);
        StackTraceElement obfuscatedElement = new StackTraceElement(
                "UnknownClass", "obfuscatedSymbol", null, -1);

        StackTraceElement deobfuscatedElement = deobfuscator.resymbolize(
                obfuscatedElement, STRONG_NAME);

        assertThat(deobfuscatedElement.getClassName()).isEqualTo("org.example.client.Widget");
        assertThat(deobfuscatedElement.getMethodName()).isEqualTo("render");
        assertThat(deobfuscatedElement.getFileName()).isEqualTo("Widget.java");
        assertThat(deobfuscatedElement.getLineNumber()).isEqualTo(37);
    }
}
