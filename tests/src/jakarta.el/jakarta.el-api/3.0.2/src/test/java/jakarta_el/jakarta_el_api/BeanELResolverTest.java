/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_el.jakarta_el_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.FeatureDescriptor;
import java.util.Iterator;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

import org.junit.jupiter.api.Test;

public class BeanELResolverTest {
    @Test
    void readsBeanPropertyWithGetter() {
        BeanELResolver resolver = new BeanELResolver();
        TestELContext context = new TestELContext();
        SampleBean bean = new SampleBean("initial");

        Object value = resolver.getValue(context, bean, "message");

        assertThat(value).isEqualTo("initial");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    @Test
    void writesBeanPropertyWithSetter() {
        BeanELResolver resolver = new BeanELResolver();
        TestELContext context = new TestELContext();
        SampleBean bean = new SampleBean("initial");

        resolver.setValue(context, bean, "message", "updated");

        assertThat(bean.getMessage()).isEqualTo("updated");
        assertThat(context.isPropertyResolved()).isTrue();
    }

    public static final class SampleBean {
        private String message;

        public SampleBean(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    private static final class TestELContext extends ELContext {
        private final ELResolver resolver = new NoOpELResolver();

        @Override
        public ELResolver getELResolver() {
            return resolver;
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

    private static final class NoOpELResolver extends ELResolver {
        @Override
        public Object getValue(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public Class<?> getType(ELContext context, Object base, Object property) {
            return null;
        }

        @Override
        public void setValue(ELContext context, Object base, Object property, Object value) {
        }

        @Override
        public boolean isReadOnly(ELContext context, Object base, Object property) {
            return true;
        }

        @Override
        public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
            return null;
        }

        @Override
        public Class<?> getCommonPropertyType(ELContext context, Object base) {
            return Object.class;
        }
    }
}
