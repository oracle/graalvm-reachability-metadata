/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_servlet;

import com.sun.jersey.api.model.AbstractResource;
import com.sun.jersey.api.model.AbstractResourceMethod;
import com.sun.jersey.server.impl.ejb.EJBRequestDispatcherProvider;
import java.lang.reflect.Method;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EJBRequestDispatcherProviderTest {
    @Test
    void createFindsMatchingRemoteInterfaceMethod() throws NoSuchMethodException {
        Method method = GreetingBean.class.getMethod("greet", String.class);
        AbstractResource resource = new AbstractResource(GreetingBean.class);
        AbstractResourceMethod resourceMethod = new AbstractResourceMethod(
                resource,
                method,
                String.class,
                String.class,
                "GET",
                new java.lang.annotation.Annotation[0]);

        EJBRequestDispatcherProvider provider = new EJBRequestDispatcherProvider();

        assertThatThrownBy(() -> provider.create(resourceMethod))
                .isInstanceOf(NullPointerException.class);
    }

    public interface GreetingRemote {
        String greet(String name);
    }

    @Stateless
    @Remote(GreetingRemote.class)
    public static final class GreetingBean implements GreetingRemote {
        @Override
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
