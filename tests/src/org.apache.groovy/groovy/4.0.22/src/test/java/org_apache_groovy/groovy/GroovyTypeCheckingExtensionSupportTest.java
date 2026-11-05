/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;
import org.codehaus.groovy.transform.stc.TypeCheckingExtension;
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport.TypeCheckingDSL;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class GroovyTypeCheckingExtensionSupportTest {
    @Test
    void loadsAPrecompiledExtensionToResolveADslVariable() throws Exception {
        VariableExtension.resolutions.set(0);
        ClassNode model = AstCompilationSupport.canonicalize("GreetingDsl", """
                @groovy.transform.TypeChecked(extensions='%s')
                class GreetingDsl {
                    String greeting() { customer.toUpperCase() }
                }
                """.formatted(VariableExtension.class.getName()));
        assertFalse(model.getMethods("greeting").isEmpty());
        assertEquals(1, VariableExtension.resolutions.get());
    }

    @Test
    void initializesAPrecompiledTypeCheckingDsl() throws Exception {
        CompiledDsl.initializations.set(0);
        ClassNode model = AstCompilationSupport.canonicalize("CheckedCount", """
                @groovy.transform.TypeChecked(extensions='%s')
                class CheckedCount { int count() { 7 } }
                """.formatted(CompiledDsl.class.getName()));
        assertFalse(model.getMethods("count").isEmpty());
        assertEquals(1, CompiledDsl.initializations.get());
    }

    public static class CompiledDsl extends TypeCheckingDSL {
        public static final AtomicInteger initializations = new AtomicInteger();

        @Override
        public Object run() {
            initializations.incrementAndGet();
            return null;
        }
    }

    public static class VariableExtension extends TypeCheckingExtension {
        public static final AtomicInteger resolutions = new AtomicInteger();

        public VariableExtension(StaticTypeCheckingVisitor visitor) {
            super(visitor);
        }

        @Override
        public boolean handleUnresolvedVariableExpression(VariableExpression variable) {
            if (!"customer".equals(variable.getName())) {
                return false;
            }
            storeType(variable, ClassHelper.STRING_TYPE);
            resolutions.incrementAndGet();
            return true;
        }
    }
}
