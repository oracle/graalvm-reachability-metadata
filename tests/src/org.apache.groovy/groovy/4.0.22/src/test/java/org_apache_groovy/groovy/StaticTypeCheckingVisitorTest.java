/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.ClosureSignatureHint;
import groovy.transform.stc.ClosureSignatureConflictResolver;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class StaticTypeCheckingVisitorTest {
    @Test
    void infersClosureParametersFromThePublicCollectSignature() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("Names", """
                @groovy.transform.TypeChecked
                class Names {
                    static List<String> uppercase() {
                        ['ada', 'lin'].collect { it.toUpperCase() }
                    }
                }
                """);
        assertNotNull(model.getDeclaredMethod("uppercase", Parameter.EMPTY_ARRAY));
        assertEquals("java.util.List", model.getDeclaredMethod("uppercase", Parameter.EMPTY_ARRAY).getReturnType().getName());
    }

    @Test
    void resolvesAnAmbiguousClosureSignatureUsingAConfiguredResolver() throws Exception {
        TextResolver.resolutions.set(0);
        ClassNode model = AstCompilationSupport.canonicalize("ResolvedClosure", """
                import org_apache_groovy.groovy.StaticTypeCheckingVisitorTest.Hints
                @groovy.transform.TypeChecked
                class ResolvedClosure {
                    String label() { Hints.choose { it.toUpperCase() } }
                }
                """);
        assertNotNull(model.getDeclaredMethod("label", Parameter.EMPTY_ARRAY));
        assertEquals(1, TextResolver.resolutions.get());
    }

    public static class Hints {
        public static String choose(@ClosureParams(value = AmbiguousHint.class,
                conflictResolutionStrategy = TextResolver.class) Closure<String> action) {
            return action.call("parcel");
        }
    }

    public static class AmbiguousHint extends ClosureSignatureHint {
        @Override
        public List<ClassNode[]> getClosureSignatures(MethodNode method, SourceUnit source,
                CompilationUnit unit, String[] options, ASTNode usage) {
            return List.of(new ClassNode[]{ClassHelper.STRING_TYPE}, new ClassNode[]{ClassHelper.Integer_TYPE});
        }
    }

    public static class TextResolver extends ClosureSignatureConflictResolver {
        public static final AtomicInteger resolutions = new AtomicInteger();

        @Override
        public List<ClassNode[]> resolve(List<ClassNode[]> candidates, ClassNode receiver, Expression arguments,
                ClosureExpression closure, MethodNode method, SourceUnit source, CompilationUnit unit, String[] options) {
            resolutions.incrementAndGet();
            return List.<ClassNode[]>of(candidates.get(0));
        }
    }
}
