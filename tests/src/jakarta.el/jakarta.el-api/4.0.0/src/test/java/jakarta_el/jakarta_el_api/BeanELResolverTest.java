/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.el.BeanELResolver;
import jakarta.el.ELContext;
import jakarta.el.ELResolver;
import jakarta.el.FunctionMapper;
import jakarta.el.VariableMapper;

import org.junit.jupiter.api.Test;

public class BeanELResolverTest {

    @Test
    void getsJavaBeansPropertyValue() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        SampleBean bean = new SampleBean("initial");

        Object value = resolver.getValue(context, bean, "name");

        assertThat(value).isEqualTo("initial");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void setsJavaBeansPropertyValue() {
        BeanELResolver resolver = new BeanELResolver();
        SimpleELContext context = new SimpleELContext();
        SampleBean bean = new SampleBean("initial");

        resolver.setValue(context, bean, "name", "updated");

        assertThat(bean.getName()).isEqualTo("updated");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    public static final class SampleBean {
        private String name;

        public SampleBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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
