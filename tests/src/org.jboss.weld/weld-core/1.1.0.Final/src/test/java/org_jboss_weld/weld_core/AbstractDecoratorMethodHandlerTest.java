/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.weld.bean.proxy.AbstractDecoratorMethodHandler;
import org.jboss.weld.introspector.WeldClass;
import org.jboss.weld.metadata.TypeStore;
import org.jboss.weld.resources.ClassTransformer;
import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class AbstractDecoratorMethodHandlerTest {
    @Test
    void invokesConcreteProceedMethod() throws Throwable {
        ClassTransformer transformer = new ClassTransformer(new TypeStore());
        try {
            WeldClass<ReflectionFixture> type = transformer.loadClass(ReflectionFixture.class);
            ReflectionFixture bean = new ReflectionFixture();
            AbstractDecoratorMethodHandler handler = new AbstractDecoratorMethodHandler(type, null, bean);
            Method method = SecureReflections.getMethod(ReflectionFixture.class, "greet", String.class);
            assertThat(handler.invoke(bean, method, method, new Object[] {"decorator"}))
                    .isEqualTo("Hello decorator");
            assertThat(bean.label).isEqualTo("Hello decorator");
        } finally {
            transformer.cleanup();
        }
    }

    @Test
    void dispatchesAbstractMethodToInjectedDelegate() throws Throwable {
        ClassTransformer transformer = new ClassTransformer(new TypeStore());
        try {
            WeldClass<ReflectionFixture> type = transformer.loadClass(ReflectionFixture.class);
            ReflectionFixture bean = new ReflectionFixture();
            AbstractDecoratorMethodHandler handler = new AbstractDecoratorMethodHandler(type, null, bean);
            Method method = SecureReflections.getMethod(Greeting.class, "greet", String.class);
            assertThat(handler.invoke(bean, method, null, new Object[] {"delegate"}))
                    .isEqualTo("Hello delegate");
            assertThat(bean.label).isEqualTo("Hello delegate");
        } finally {
            transformer.cleanup();
        }
    }

    public interface Greeting {
        String greet(String name);
    }
}
