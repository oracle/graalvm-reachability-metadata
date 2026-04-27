/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;
import org.junit.jupiter.api.Test;

public class BeanELResolverTest {

    @Test
    void getValueInvokesBeanGetter() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        ProfileBean bean = new ProfileBean("Duke");

        Object value = resolver.getValue(context, bean, "displayName");

        assertThat(value).isEqualTo("Duke");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void setValueInvokesBeanSetter() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        ProfileBean bean = new ProfileBean("before");

        resolver.setValue(context, bean, "displayName", "after");

        assertThat(bean.getDisplayName()).isEqualTo("after");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    public static final class ProfileBean {
        private String displayName;

        public ProfileBean(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class SimpleELContext extends ELContext {

        @Override
        public ELResolver getELResolver() {
            return null;
        }

        @Override
        public FunctionMapper getFunctionMapper() {
            return null;
        }

        @Override
        public VariableMapper getVariableMapper() {
            return null;
        }
    }
}
