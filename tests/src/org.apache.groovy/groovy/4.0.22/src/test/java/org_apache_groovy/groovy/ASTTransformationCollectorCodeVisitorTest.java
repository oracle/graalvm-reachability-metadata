/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AnnotationCollectorTransform;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.groovy.ast.tools.AnnotatedNodeUtils;
import org.codehaus.groovy.ast.Parameter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ASTTransformationCollectorCodeVisitorTest {
    @Test
    void discoversAndAppliesAnAnnotationDeclaredTransform() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("Receipt", """
                @groovy.transform.ToString(includeNames=true)
                class Receipt { String reference }
                """);
        assertNotNull(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY));
        assertTrue(AnnotatedNodeUtils.isGenerated(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY)));
    }

    @Test
    void loadsACustomAnnotationCollectorProcessor() throws Exception {
        CollectorProcessor.invocations.set(0);
        ClassNode model = AstCompilationSupport.canonicalize("CollectedParcel", """
                @groovy.transform.AnnotationCollector(processor='%s')
                @groovy.transform.ToString
                @interface Labelled {}
                @Labelled
                class CollectedParcel { String reference }
                """.formatted(CollectorProcessor.class.getName()));
        assertNotNull(model.getDeclaredMethod("toString", Parameter.EMPTY_ARRAY));
        assertTrue(CollectorProcessor.invocations.get() > 0);
    }

    public static class CollectorProcessor extends AnnotationCollectorTransform {
        public static final AtomicInteger invocations = new AtomicInteger();

        @Override
        public List<AnnotationNode> visit(AnnotationNode collector, AnnotationNode usage, AnnotatedNode target, SourceUnit source) {
            invocations.incrementAndGet();
            return super.visit(collector, usage, target, source);
        }
    }
}
