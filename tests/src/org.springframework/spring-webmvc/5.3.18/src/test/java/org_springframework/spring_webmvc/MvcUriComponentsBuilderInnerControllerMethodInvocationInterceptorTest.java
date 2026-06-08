/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class MvcUriComponentsBuilderInnerControllerMethodInvocationInterceptorTest {

    @Test
    void controllerProxyCanUseDefaultConstructorFallbackWhenObjenesisIsDisabled() {
        String propertyName = "spring.objenesis.ignore";
        String previousValue = System.getProperty(propertyName);
        System.setProperty(propertyName, "true");
        ConstructorFallbackController.constructorInvocations.set(0);

        try {
            ConstructorFallbackController controller =
                    MvcUriComponentsBuilder.controller(ConstructorFallbackController.class);

            controller.handle("spring");

            String path = MvcUriComponentsBuilder.fromMethodCall(UriComponentsBuilder.fromPath(""), controller)
                    .build()
                    .toUriString();

            assertThat(ConstructorFallbackController.constructorInvocations).hasValue(1);
            assertThat(path).isEqualTo("/fallback/spring");
        }
        catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
        finally {
            if (previousValue == null) {
                System.clearProperty(propertyName);
            }
            else {
                System.setProperty(propertyName, previousValue);
            }
        }
    }

    @RequestMapping("/fallback")
    public static class ConstructorFallbackController {
        static final AtomicInteger constructorInvocations = new AtomicInteger();

        public ConstructorFallbackController() {
            constructorInvocations.incrementAndGet();
        }

        @RequestMapping("/{id}")
        public void handle(@PathVariable("id") String id) {
        }
    }
}
