/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import jakarta.el.ELClass;
import jakarta.el.ELContext;
import jakarta.el.ELManager;
import jakarta.el.ExpressionFactory;
import jakarta.el.StaticFieldELResolver;
import jakarta.el.ValueExpression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StaticFieldELResolverTest {

    @Test
    void readsPublicStaticFieldValue() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.getValue(newContext(), new ELClass(StaticFieldTarget.class), "GREETING");

        assertThat(value).isEqualTo("hello");
    }

    @Test
    void resolvesImportedPublicStaticFieldAsReadOnlyExpression() {
        ELManager manager = new ELManager();
        ELContext context = manager.getELContext();
        String className = StaticFieldTarget.class.getName();
        String expressionName = className.substring(className.lastIndexOf('.') + 1);
        manager.importClass(className);
        ValueExpression expression = ExpressionFactory.newInstance()
                .createValueExpression(context, "${" + expressionName + ".GREETING}", Object.class);
        Object value = expression.getValue(context);

        assertThat(expression.getType(context)).isNull();
        assertThat(expression.isReadOnly(context)).isTrue();
        assertThat(value).isEqualTo("hello");
    }

    @Test
    void invokesConstructorWhenParameterTypesAreInferredFromArguments() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.invoke(
                newContext(),
                new ELClass(ConstructorTarget.class),
                "<init>",
                null,
                new Object[]{"inferred"});

        assertThat(value).isInstanceOf(ConstructorTarget.class);
        assertThat(((ConstructorTarget) value).getLabel()).isEqualTo("ctor:inferred");
    }

    @Test
    void invokesPublicStaticMethod() {
        StaticFieldELResolver resolver = new StaticFieldELResolver();

        Object value = resolver.invoke(
                newContext(),
                new ELClass(StaticMethodTarget.class),
                "describe",
                new Class<?>[]{String.class},
                new Object[]{"value"});

        assertThat(value).isEqualTo("static:value");
    }

    private static ELContext newContext() {
        return new ELManager().getELContext();
    }

    public static final class StaticFieldTarget {
        public static final String GREETING = "hello";

        private StaticFieldTarget() {
        }
    }

    public static final class ConstructorTarget {
        private final String label;

        public ConstructorTarget(String value) {
            this.label = "ctor:" + value;
        }

        public String getLabel() {
            return label;
        }
    }

    public static final class StaticMethodTarget {
        private StaticMethodTarget() {
        }

        public static String describe(String value) {
            return "static:" + value;
        }
    }
}
