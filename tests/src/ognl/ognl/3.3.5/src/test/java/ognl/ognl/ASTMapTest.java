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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

public class ASTMapTest {
    @Test
    void createsDefaultLinkedHashMapLiteral() throws OgnlException {
        final Object expression = Ognl.parseExpression("#{\"first\" : 1, \"second\" : 2}");
        final OgnlContext context = newContext();

        final Object value = Ognl.getValue(expression, context, (Object) null);

        final Map<?, ?> map = (Map<?, ?>) value;
        assertThat(value).isInstanceOf(LinkedHashMap.class);
        assertThat(map.keySet().toArray()).containsExactly("first", "second");
        assertThat(map.get("first")).isEqualTo(1);
        assertThat(map.get("second")).isEqualTo(2);
    }

    @Test
    void createsExplicitMapImplementationLiteral() throws OgnlException {
        final Object expression = Ognl.parseExpression("#@java.util.TreeMap@{\"second\" : 2, \"first\" : 1}");
        final OgnlContext context = newContext();

        final Object value = Ognl.getValue(expression, context, (Object) null);

        final Map<?, ?> map = (Map<?, ?>) value;
        assertThat(value).isInstanceOf(TreeMap.class);
        assertThat(map.keySet().toArray()).containsExactly("first", "second");
        assertThat(map.get("first")).isEqualTo(1);
        assertThat(map.get("second")).isEqualTo(2);
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
}
