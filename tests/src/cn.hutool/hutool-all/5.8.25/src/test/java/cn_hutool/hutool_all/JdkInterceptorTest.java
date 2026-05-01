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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JdkInterceptorTest {
    @Test
    public void invokesTargetMethodThroughJdkProxy() {
        GreetingService target = new DefaultGreetingService("Hello");
        RecordingAspect aspect = new RecordingAspect();

        GreetingService proxy = ProxyUtil.newProxyInstance(
                JdkInterceptorTest.class.getClassLoader(),
                new JdkInterceptor(target, aspect),
                GreetingService.class);
        String greeting = proxy.greet("Hutool");

        assertThat(greeting).isEqualTo("Hello, Hutool");
        assertThat(aspect.getEvents()).containsExactly("before:greet", "after:greet=Hello, Hutool");
        assertThat(aspect.getTargets()).containsOnly(target);
        assertThat(aspect.getArguments()).containsExactly("Hutool");
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class DefaultGreetingService implements GreetingService {
        private final String salutation;

        public DefaultGreetingService(String salutation) {
            this.salutation = salutation;
        }

        @Override
        public String greet(String name) {
            return salutation + ", " + name;
        }
    }

    public static class RecordingAspect implements Aspect {
        private final List<String> events = new ArrayList<>();
        private final List<Object> targets = new ArrayList<>();
        private final List<Object> arguments = new ArrayList<>();

        @Override
        public boolean before(Object target, Method method, Object[] args) {
            targets.add(target);
            arguments.add(args[0]);
            events.add("before:" + method.getName());
            return true;
        }

        @Override
        public boolean after(Object target, Method method, Object[] args, Object returnVal) {
            targets.add(target);
            events.add("after:" + method.getName() + "=" + returnVal);
            return true;
        }

        @Override
        public boolean afterException(Object target, Method method, Object[] args, Throwable e) {
            events.add("exception:" + method.getName());
            return true;
        }

        public List<String> getEvents() {
            return events;
        }

        public List<Object> getTargets() {
            return targets;
        }

        public List<Object> getArguments() {
            return arguments;
        }
    }
}
