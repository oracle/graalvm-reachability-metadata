/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.ASTCtor;
import ognl.AbstractMemberAccess;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Member;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ASTCtorTest {
    @Test
    void createsTypedArrayFromSizeExpression() throws OgnlException {
        final OgnlContext context = newContext();
        final Object expression = Ognl.parseExpression("new java.lang.String[3]");

        final Object value = Ognl.getValue(expression, context, (Object) null);

        assertThat(value).isInstanceOf(String[].class);
        assertThat((String[]) value).containsExactly(null, null, null);
    }

    @Test
    void emitsSourceForConstructorWithArguments() throws OgnlException {
        final OgnlContext context = newContext();
        final String className = SourceConstructorFixture.class.getName();
        final ASTCtor expression = (ASTCtor) Ognl.parseExpression("new " + className + "(\"Ada\", 7)");

        final String source = expression.toGetSourceString(context, null);

        assertThat(source).isEqualTo("new " + className + "(\"Ada\", 7)");
        assertThat(context.getCurrentObject()).isInstanceOf(SourceConstructorFixture.class);
        assertThat(((SourceConstructorFixture) context.getCurrentObject()).description()).isEqualTo("Ada:7");
        assertThat(context.getCurrentType()).isEqualTo(SourceConstructorFixture.class);
        assertThat(context.getCurrentAccessor()).isEqualTo(SourceConstructorFixture.class);
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

    public static final class SourceConstructorFixture {
        private final String name;
        private final int count;

        public SourceConstructorFixture(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String description() {
            return name + ":" + count;
        }
    }
}
