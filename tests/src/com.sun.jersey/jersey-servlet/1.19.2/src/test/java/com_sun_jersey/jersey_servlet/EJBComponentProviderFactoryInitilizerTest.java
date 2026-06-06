/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_servlet;

import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.server.impl.ejb.EJBComponentProviderFactoryInitilizer;
import com.sun.jersey.server.impl.ejb.EJBExceptionMapper;
import javax.naming.NamingException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EJBComponentProviderFactoryInitilizerTest {
    private InterceptorBinder interceptorBinder;

    @BeforeAll
    static void installInitialContextFactory() throws NamingException {
        ManagedBeanComponentProviderFactoryInnerManagedBeanComponentProviderTest.InitialContextSupport.install();
    }

    @BeforeEach
    void setUp() {
        interceptorBinder = new InterceptorBinder();
        ManagedBeanComponentProviderFactoryInnerManagedBeanComponentProviderTest.InitialContextSupport
                .setInterceptorBinder(interceptorBinder);
    }

    @Test
    void initializeRegistersEjbInterceptorAndResourceConfigProviders() {
        ResourceConfig resourceConfig = new DefaultResourceConfig();

        EJBComponentProviderFactoryInitilizer.initialize(resourceConfig);

        assertThat(interceptorBinder.getInterceptor())
                .isNotNull()
                .extracting(interceptor -> interceptor.getClass().getName())
                .isEqualTo("com.sun.jersey.server.impl.ejb.EJBInjectionInterceptor");
        assertThat(resourceConfig.getSingletons())
                .singleElement()
                .extracting(singleton -> singleton.getClass().getName())
                .isEqualTo("com.sun.jersey.server.impl.ejb.EJBComponentProviderFactory");
        assertThat(resourceConfig.getClasses()).contains(EJBExceptionMapper.class);
    }

    public static final class InterceptorBinder {
        private Object interceptor;

        public void registerInterceptor(Object interceptor) {
            this.interceptor = interceptor;
        }

        private Object getInterceptor() {
            return interceptor;
        }
    }

}
