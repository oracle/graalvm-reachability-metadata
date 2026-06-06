/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ognl.ognl;

import ognl.AbstractMemberAccess;
import ognl.ObjectPropertyAccessor;
import ognl.OgnlContext;
import ognl.OgnlException;
import ognl.OgnlRuntime;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectPropertyAccessorTest {
    @Test
    void resolvesPublicFieldTypeWhenPropertyHasNoReadMethod() {
        final OgnlContext context = newContext(new AllowAllMemberAccess());
        final ObjectPropertyAccessor accessor = new ObjectPropertyAccessor();
        final PublicFieldOnlyFixture fixture = new PublicFieldOnlyFixture();

        final Class<?> propertyClass = accessor.getPropertyClass(context, fixture, "\"displayName\"");

        assertThat(propertyClass).isEqualTo(String.class);
    }

    @Test
    void emitsPublicFieldSourceAccessorWhenPropertyHasNoReadMethod() {
        final OgnlContext context = newContext(new AllowAllMemberAccess());
        final ObjectPropertyAccessor accessor = new ObjectPropertyAccessor();
        final PublicFieldOnlyFixture fixture = new PublicFieldOnlyFixture();

        final String sourceAccessor = accessor.getSourceAccessor(context, fixture, "\"displayName\"");

        assertThat(sourceAccessor).isEqualTo(".displayName");
        assertThat(context.getCurrentType()).isEqualTo(String.class);
        assertThat(context.getCurrentAccessor()).isEqualTo(PublicFieldOnlyFixture.class);
    }

    @Test
    void returnsNotFoundWhenAccessCheckRejectsSetterPath() throws OgnlException {
        final OgnlContext context = newContext(new SetterAccessDenyingMemberAccess());
        final ObjectPropertyAccessor accessor = new ObjectPropertyAccessor();
        final SetterOnlyFixture fixture = new SetterOnlyFixture();

        final Object result = accessor.setPossibleProperty(context, fixture, "alias", "updated");

        assertThat(result).isSameAs(OgnlRuntime.NotFound);
        assertThat(fixture.assignedValue()).isNull();
    }

    private static OgnlContext newContext(AbstractMemberAccess memberAccess) {
        return new OgnlContext(null, null, memberAccess);
    }

    private static final class AllowAllMemberAccess extends AbstractMemberAccess {
        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            return true;
        }
    }

    private static final class SetterAccessDenyingMemberAccess extends AbstractMemberAccess {
        @Override
        public boolean isAccessible(Map context, Object target, Member member, String propertyName) {
            return !(member instanceof Method && "setAlias".equals(member.getName()));
        }
    }

    public static final class PublicFieldOnlyFixture {
        public String displayName = "Ada";
    }

    public static final class SetterOnlyFixture {
        private String assignedValue;

        public void setAlias(String assignedValue) {
            this.assignedValue = assignedValue;
        }

        public String assignedValue() {
            return assignedValue;
        }
    }
}
