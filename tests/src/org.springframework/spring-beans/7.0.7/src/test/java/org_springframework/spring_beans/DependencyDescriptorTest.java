/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class DependencyDescriptorTest {
    @Test
    void restoresFieldMethodAndConstructorDependenciesAfterSerialization() throws Exception {
        List<DependencyDescriptor> descriptors = List.of(
                new DependencyDescriptor(Client.class.getDeclaredField("service"), true),
                new DependencyDescriptor(new MethodParameter(
                        Client.class.getDeclaredMethod("configure", Service.class), 0), true),
                new DependencyDescriptor(new MethodParameter(
                        Client.class.getDeclaredConstructor(Service.class), 0), true));
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        Service service = new Service();
        factory.registerSingleton("service", service);
        for (DependencyDescriptor descriptor : descriptors) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(descriptor);
            }
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                DependencyDescriptor restored = (DependencyDescriptor) input.readObject();
                assertEquals(Service.class, restored.getDependencyType());
                assertSame(service, factory.resolveDependency(restored, "client"));
            }
        }
    }

    public static class Service {
    }

    public static class Client {
        private Service service;

        public Client(Service service) {
            this.service = service;
        }

        public void configure(Service service) {
            this.service = service;
        }
    }
}
