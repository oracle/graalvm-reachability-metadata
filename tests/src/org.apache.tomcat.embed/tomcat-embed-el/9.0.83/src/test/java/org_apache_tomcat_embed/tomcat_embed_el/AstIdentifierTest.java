/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import javax.el.ELContext;
import javax.el.ELManager;
import javax.el.ValueExpression;

import org.apache.el.ExpressionFactoryImpl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AstIdentifierTest {

    @Test
    void resolvesImportedPublicStaticFieldFromBareIdentifier() {
        ELManager manager = new ELManager();
        ELContext context = manager.getELContext();
        ExpressionFactoryImpl expressionFactory = new ExpressionFactoryImpl();

        manager.importStatic(StaticFieldTarget.class.getName() + ".GREETING");

        ValueExpression expression = expressionFactory.createValueExpression(context, "${GREETING}", Object.class);
        Object value = expression.getValue(context);

        assertThat(value).isEqualTo("hello");
    }

    public static final class StaticFieldTarget {
        public static final String GREETING = "hello";

        private StaticFieldTarget() {
        }
    }
}
