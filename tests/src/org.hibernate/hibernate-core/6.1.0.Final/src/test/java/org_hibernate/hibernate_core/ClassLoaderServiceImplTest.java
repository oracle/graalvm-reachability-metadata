/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.SessionEventListener;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderServiceImplTest {

    @Test
    public void locatesResourcesAndPackages() throws Exception {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            ClassLoaderService classLoaderService = registry.getService(ClassLoaderService.class);

            try (InputStream direct = classLoaderService.locateResourceStream("hibernate.properties");
                    InputStream stripped = classLoaderService.locateResourceStream("/hibernate.properties")) {
                assertThat(direct).isNotNull();
                assertThat(stripped).isNotNull();
            }

            assertThat(classLoaderService.packageForNameOrNull("org.hibernate.query.sqm"))
                    .extracting(Package::getName)
                    .isEqualTo("org.hibernate.query.sqm");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    public void generatesAProxyForAHibernateServiceContract() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            ClassLoaderService classLoaderService = registry.getService(ClassLoaderService.class);
            RecordingInvocationHandler handler = new RecordingInvocationHandler();
            SessionEventListener listener = classLoaderService.generateProxy(
                    handler,
                    SessionEventListener.class
            );

            listener.jdbcConnectionAcquisitionStart();

            assertThat(handler.getInvokedMethod()).isEqualTo("jdbcConnectionAcquisitionStart");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class RecordingInvocationHandler implements InvocationHandler {
        private String invokedMethod;

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            invokedMethod = method.getName();
            return null;
        }

        public String getInvokedMethod() {
            return invokedMethod;
        }
    }
}
