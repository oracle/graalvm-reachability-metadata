/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_api;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.apache.cxf.common.injection.ResourceInjector;
import org.apache.cxf.resource.DefaultResourceManager;
import org.apache.cxf.resource.SinglePropertyResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceInjectorTest {
    @Test
    void injectsFieldsAndSettersAndInvokesLifecycleCallbacks() {
        DefaultResourceManager resources = new DefaultResourceManager(
                new SinglePropertyResolver("greeting", "Hello"));
        resources.addResourceResolver(new SinglePropertyResolver("recipient", "Ada"));
        ResourceInjector injector = new ResourceInjector(resources);
        GreetingService service = new GreetingService();

        injector.inject(service);
        injector.construct(service);
        assertEquals("Hello, Ada", service.getGreeting());
        assertEquals(List.of("started: Hello, Ada"), service.getEvents());

        injector.destroy(service);
        assertEquals(List.of("started: Hello, Ada", "stopped: Ada"), service.getEvents());
    }

    @Test
    void resolvesAClassLevelResourceUsingItsSetter() {
        DefaultResourceManager resources = new DefaultResourceManager(
                new SinglePropertyResolver("destination", "orders"));
        ResourceInjector injector = new ResourceInjector(resources);
        DestinationService service = new DestinationService();

        injector.inject(service);
        assertEquals("orders: ready", service.describe());
    }

    public static class GreetingService {
        @Resource(name = "greeting")
        private String greeting;

        private String recipient;
        private final List<String> events = new ArrayList<>();

        @Resource(name = "recipient")
        public void setRecipient(String value) {
            recipient = value;
        }

        @PostConstruct
        private void start() {
            events.add("started: " + getGreeting());
        }

        @PreDestroy
        private void stop() {
            events.add("stopped: " + recipient);
        }

        public String getGreeting() {
            return greeting + ", " + recipient;
        }

        public List<String> getEvents() {
            return events;
        }
    }

    @Resource(name = "destination", type = String.class)
    public static class DestinationService {
        private String destination;

        public void setDestination(String value) {
            destination = value;
        }

        public String describe() {
            return destination + ": ready";
        }
    }
}
