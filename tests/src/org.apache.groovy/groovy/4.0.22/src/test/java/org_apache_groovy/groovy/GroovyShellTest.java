/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.util.FactoryBuilderSupport;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GroovyShellTest {
    @Test
    void enforcesTheSecurityPolicySuppliedToTheConfigurationBuilder() throws Exception {
        GroovyShell shell = GroovyShell.withConfig(new Closure<Void>(this) {
            public Void doCall() {
                FactoryBuilderSupport builder = (FactoryBuilderSupport) getDelegate();
                builder.invokeMethod("secureAst", new Object[]{new LinkedHashMap<>(Map.of("packageAllowed", false))});
                return null;
            }
        });
        try (var loader = shell.getClassLoader()) {
            MultipleCompilationErrorsException failure = assertThrows(MultipleCompilationErrorsException.class,
                    () -> shell.parse("package restricted\n42"));
            assertTrue(failure.getMessage().contains("Package definitions are not allowed"));
        }
    }
}
