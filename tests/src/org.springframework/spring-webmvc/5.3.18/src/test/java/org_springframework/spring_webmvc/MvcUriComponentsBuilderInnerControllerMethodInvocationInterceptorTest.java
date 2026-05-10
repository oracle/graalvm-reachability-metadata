/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

public class MvcUriComponentsBuilderInnerControllerMethodInvocationInterceptorTest {
    private static final String IGNORE_OBJENESIS_PROPERTY_NAME = "spring.objenesis.ignore";

    @Test
    void controllerProxyFallsBackToReflectiveDefaultConstructor() {
        String previousValue = System.getProperty(IGNORE_OBJENESIS_PROPERTY_NAME);
        System.setProperty(IGNORE_OBJENESIS_PROPERTY_NAME, "true");
        try {
            assertControllerProxyUsesRecordedInvocation();
        } catch (RuntimeException exception) {
            if (!hasUnsupportedFeatureErrorCause(exception)) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            restoreObjenesisProperty(previousValue);
        }
    }

    private static void assertControllerProxyUsesRecordedInvocation() {
        SampleController controller = MvcUriComponentsBuilder.controller(SampleController.class);
        controller.show("42");

        String uri = MvcUriComponentsBuilder.fromMethodCall(
                        UriComponentsBuilder.fromUriString("https://example.test"),
                        controller)
                .build()
                .toUriString();

        assertThat(uri).isEqualTo("https://example.test/widgets/42");
    }

    private static boolean hasUnsupportedFeatureErrorCause(Throwable throwable) {
        Throwable current = throwable.getCause();
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void restoreObjenesisProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(IGNORE_OBJENESIS_PROPERTY_NAME);
        } else {
            System.setProperty(IGNORE_OBJENESIS_PROPERTY_NAME, previousValue);
        }
    }

    @RequestMapping("/widgets")
    public static class SampleController {
        @RequestMapping("/{id}")
        public void show(@PathVariable("id") String id) {
        }
    }
}
