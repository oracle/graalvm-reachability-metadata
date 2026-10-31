/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;

final class AstCompilationSupport {
    private AstCompilationSupport() {
    }

    static ClassNode canonicalize(String className, String source) throws Exception {
        return canonicalize(className, source, new CompilerConfiguration());
    }

    static ClassNode canonicalize(String className, String source, CompilerConfiguration configuration) throws Exception {
        configuration.getOptimizationOptions().put("asmResolving", false);
        try (GroovyClassLoader loader = new GroovyClassLoader(AstCompilationSupport.class.getClassLoader(), configuration)) {
            CompilationUnit unit = new CompilationUnit(configuration, null, loader);
            unit.addSource(className + ".groovy", source);
            // Stop before bytecode generation and class definition.
            unit.compile(Phases.INSTRUCTION_SELECTION);
            return unit.getClassNode(className);
        }
    }
}
