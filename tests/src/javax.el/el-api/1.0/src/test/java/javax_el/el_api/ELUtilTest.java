/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_el.el_api;

import javax.el.BeanELResolver;
import javax.el.ELContext;
import javax.el.ELResolver;
import javax.el.ELException;
import javax.el.FunctionMapper;
import javax.el.VariableMapper;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ELUtilTest {
    @Test
    void formatsExceptionMessageFromPrivateResourceBundle() {
        BeanELResolver resolver = new BeanELResolver();
        TestELContext context = new TestELContext(resolver);
        context.setLocale(new Locale("en", "ELUTIL"));
        MessageBean bean = new MessageBean();

        assertThatThrownBy(() -> resolver.setValue(context, bean, "count", "not a number"))
                .isInstanceOf(ELException.class)
                .hasMessageContaining("Can't set property")
                .hasMessageContaining("count");
    }

    public static final class MessageBean {
        private int count;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    private static final class TestELContext extends ELContext {
        private final ELResolver resolver;

        TestELContext(ELResolver resolver) {
            this.resolver = resolver;
        }

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
}
