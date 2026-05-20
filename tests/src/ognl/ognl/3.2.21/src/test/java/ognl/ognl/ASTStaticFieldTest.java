/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.ASTStaticField;
import ognl.AbstractMemberAccess;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Member;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ASTStaticFieldTest {
    @Test
    void emitsSourceForPublicStaticField() throws OgnlException {
        final OgnlContext context = newContext();
        final String className = StaticFieldFixture.class.getName();
        final ASTStaticField expression = (ASTStaticField) Ognl.parseExpression("@" + className + "@TEXT");

        final String source = expression.toGetSourceString(context, null);

        assertThat(source).isEqualTo(className + ".TEXT");
        assertThat(context.getCurrentObject()).isEqualTo("available through a public static field");
        assertThat(context.getCurrentType()).isEqualTo(String.class);
        assertThat(expression.getGetterClass()).isEqualTo(String.class);
    }

    private static OgnlContext newContext() {
        return new OgnlContext(null, null, new AllowAllMemberAccess());
    }

    private static final class AllowAllMemberAccess extends AbstractMemberAccess {
        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            return true;
        }
    }

    public static final class StaticFieldFixture {
        public static final String TEXT = "available through a public static field";
    }
}
