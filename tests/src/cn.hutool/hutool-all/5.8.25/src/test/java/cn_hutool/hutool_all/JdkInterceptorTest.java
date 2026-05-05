/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.aop.ProxyUtil;
import cn.hutool.aop.aspects.Aspect;
import cn.hutool.aop.interceptor.JdkInterceptor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdkInterceptorTest {

    @Test
    void invokesTargetMethodAndAspectCallbacksThroughJdkProxy() {
        GreetingServiceImpl target = new GreetingServiceImpl("Hello");
        RecordingAspect aspect = new RecordingAspect();

        GreetingService proxy = ProxyUtil.newProxyInstance(
                GreetingService.class.getClassLoader(), new JdkInterceptor(target, aspect), GreetingService.class);
        String greeting = proxy.greet("Ada");

        assertThat(greeting).isEqualTo("Hello, Ada");
        assertThat(target.greetedNames()).containsExactly("Ada");
        assertThat(aspect.events()).containsExactly(
                "before:greet:[Ada]",
                "after:greet:Hello, Ada");
    }

    public interface GreetingService {
        String greet(String name);
    }

    private static final class GreetingServiceImpl implements GreetingService {
        private final String salutation;
        private final List<String> greetedNames = new ArrayList<>();

        private GreetingServiceImpl(String salutation) {
            this.salutation = salutation;
        }

        @Override
        public String greet(String name) {
            greetedNames.add(name);
            return salutation + ", " + name;
        }

        List<String> greetedNames() {
            return greetedNames;
        }
    }

    private static final class RecordingAspect implements Aspect {
        private final List<String> events = new ArrayList<>();

        @Override
        public boolean before(Object target, Method method, Object[] args) {
            assertThat(target).isInstanceOf(GreetingServiceImpl.class);
            events.add("before:" + method.getName() + ":" + Arrays.toString(args));
            return true;
        }

        @Override
        public boolean after(Object target, Method method, Object[] args, Object returnVal) {
            assertThat(target).isInstanceOf(GreetingServiceImpl.class);
            events.add("after:" + method.getName() + ":" + returnVal);
            return true;
        }

        @Override
        public boolean afterException(Object target, Method method, Object[] args, Throwable e) {
            events.add("exception:" + method.getName() + ":" + e.getClass().getSimpleName());
            return true;
        }

        List<String> events() {
            return events;
        }
    }
}
