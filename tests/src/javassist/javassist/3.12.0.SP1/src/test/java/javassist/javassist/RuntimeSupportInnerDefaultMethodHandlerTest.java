/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.RuntimeSupport;

import org.junit.jupiter.api.Test;

public class RuntimeSupportInnerDefaultMethodHandlerTest {
    @Test
    void invokesProceedMethodThroughDefaultInterceptor() throws Throwable {
        EchoService service = new EchoService("prefix");
        Method method = EchoService.class.getMethod("message", String.class, int.class);
        MethodHandler handler = RuntimeSupport.default_interceptor;

        Object result = handler.invoke(
                service,
                method,
                method,
                new Object[] {"body", Integer.valueOf(4) });

        assertThat(result).isEqualTo("prefix:body:4");
    }

    public static class EchoService {
        private final String prefix;

        public EchoService(String prefix) {
            this.prefix = prefix;
        }

        public String message(String value, int count) {
            return prefix + ":" + value + ":" + count;
        }
    }
}
