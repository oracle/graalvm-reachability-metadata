/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class TimedAspectTest {

    @Test
    void timesImplementationAnnotationThroughInterfaceProxy() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            AspectJProxyFactory factory = new AspectJProxyFactory(new GreetingServiceImpl());
            factory.setInterfaces(GreetingService.class);
            factory.addAspect(new TimedAspect(registry));
            GreetingService service = factory.getProxy();

            assertThat(service.greet("Ada")).isEqualTo("Hello Ada");
            assertThat(registry.get("service.greeting").tag("exception", "none").timer().count()).isEqualTo(1);
        } finally {
            registry.close();
        }
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static class GreetingServiceImpl implements GreetingService {
        @Override
        @Timed("service.greeting")
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
