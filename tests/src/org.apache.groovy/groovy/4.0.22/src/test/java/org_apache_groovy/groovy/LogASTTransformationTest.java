/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.codehaus.groovy.transform.LogASTTransformation.LoggingStrategy;
import org.codehaus.groovy.transform.LogASTTransformation.LoggingStrategyV2;
import groovyjarjarasm.asm.Opcodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogASTTransformationTest {
    @Test
    void addsTheNamedJavaLoggingField() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("DispatchLog", """
                @groovy.util.logging.Log(value='audit', category='delivery.audit')
                class DispatchLog {
                    void record() { audit.info('dispatched') }
                }
                """);
        FieldNode logger = model.getDeclaredField("audit");
        assertNotNull(logger);
        assertEquals("java.util.logging.Logger", logger.getType().getName());
        assertTrue(logger.isStatic());
        assertTrue(logger.isFinal());
        assertTrue(logger.getInitialExpression().getText().contains("delivery.audit"));
    }

    @Test
    void loadsANoArgumentConfigurableLoggingStrategy() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("CustomLogModel", """
                @%s(value='audit', category='shipments')
                class CustomLogModel {}
                """.formatted(ConfigurableLog.class.getCanonicalName()));
        assertEquals("shipments", model.getDeclaredField("audit").getInitialExpression().getText());
        assertTrue(model.getDeclaredField("audit").isStatic());
    }

    @Test
    void loadsANoArgumentLegacyLoggingStrategy() throws Exception {
        ClassNode model = AstCompilationSupport.canonicalize("LegacyLogModel", """
                @%s(value='audit', category='legacy.shipments')
                class LegacyLogModel {}
                """.formatted(LegacyLog.class.getCanonicalName()));
        assertEquals("legacy.shipments", model.getDeclaredField("audit").getInitialExpression().getText());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @GroovyASTTransformationClass("org.codehaus.groovy.transform.LogASTTransformation")
    public @interface ConfigurableLog {
        String value() default "log";
        String category() default "";
        Class<? extends LoggingStrategy> loggingStrategy() default ConfigurableStrategy.class;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @GroovyASTTransformationClass("org.codehaus.groovy.transform.LogASTTransformation")
    public @interface LegacyLog {
        String value() default "log";
        String category() default "";
        Class<? extends LoggingStrategy> loggingStrategy() default LegacyStrategy.class;
    }

    public static class LegacyStrategy implements LoggingStrategy {
        @Override
        public FieldNode addLoggerFieldToClass(ClassNode node, String field, String category) {
            return node.addField(field, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    ClassHelper.STRING_TYPE, new ConstantExpression(category));
        }

        @Override
        public boolean isLoggingMethod(String method) {
            return false;
        }

        @Override
        public Expression wrapLoggingMethodCall(Expression variable, String method, Expression expression) {
            return expression;
        }
    }

    public static class ConfigurableStrategy extends LegacyStrategy implements LoggingStrategyV2 {
        @Override
        public FieldNode addLoggerFieldToClass(ClassNode node, String field, String category, int modifiers) {
            return node.addField(field, modifiers, ClassHelper.STRING_TYPE, new ConstantExpression(category));
        }
    }
}
