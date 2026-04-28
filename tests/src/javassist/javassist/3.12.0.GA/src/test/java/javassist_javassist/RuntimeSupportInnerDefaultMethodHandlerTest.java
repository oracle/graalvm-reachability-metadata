/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.Serializable;

import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.ProxyObject;
import javassist.util.proxy.RuntimeSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class RuntimeSupportInnerDefaultMethodHandlerTest {
    @Test
    void defaultHandlerInvokesGeneratedProceedMethod() throws Throwable {
        assumeFalse(NativeImageConditions.isNativeImageRuntime(),
                "Native Image does not support runtime proxy class definition.");
        ProxyFactory factory = new ProxyFactory();
        factory.setUseCache(false);
        factory.setUseWriteReplace(false);
        factory.setSuperclass(DefaultHandledService.class);
        factory.setFilter(method -> method.getName().equals("message"));

        DefaultHandledService proxy = (DefaultHandledService) factory.create(
                new Class[] {String.class},
                new Object[] {"base"});

        assertThat(ProxyFactory.isProxyClass(proxy.getClass())).isTrue();
        assertThat(((ProxyObject) proxy).getHandler()).isSameAs(RuntimeSupport.default_interceptor);
        assertThat(proxy.message("javassist", 3)).isEqualTo("base:javassist:3");
        assertThat(proxy.unhandled()).isEqualTo("base:unhandled");
    }

    public static class DefaultHandledService implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String prefix;

        public DefaultHandledService(String prefix) {
            this.prefix = prefix;
        }

        public String message(String value, int count) {
            return prefix + ":" + value + ":" + count;
        }

        public String unhandled() {
            return prefix + ":unhandled";
        }
    }
}
