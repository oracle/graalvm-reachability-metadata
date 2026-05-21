/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.AbstractMemberAccess;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Member;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ASTChainTest {
    @Test
    void copiesIndexedArrayPropertyForDynamicAllSubscript() throws OgnlException {
        final IndexedArrayPropertyFixture root = new IndexedArrayPropertyFixture(
                new String[] {"alpha", "beta", "gamma"});
        final OgnlContext context = newContext();
        final Object expression = Ognl.parseExpression("values[*]");

        final Object value = Ognl.getValue(expression, context, root);

        assertThat(value).isInstanceOf(String[].class);
        assertThat((String[]) value).containsExactly("alpha", "beta", "gamma");
        assertThat(value).isNotSameAs(root.getValues());
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

    public static final class IndexedArrayPropertyFixture {
        private final String[] values;

        public IndexedArrayPropertyFixture(String[] values) {
            this.values = values;
        }

        public String[] getValues() {
            return values;
        }

        public String getValues(int index) {
            return values[index];
        }
    }
}
