/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class MvcUriComponentsBuilderInnerControllerMethodInvocationInterceptorTest {
    private static final String OBJENESIS_IGNORE_VALUE = "true";

    @Test
    void concreteControllerProxyFallsBackToDefaultConstructorWhenObjenesisIsDisabled() {
        String previousValue = System.getProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME);
        System.setProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME, OBJENESIS_IGNORE_VALUE);
        try {
            ItemController controller = MvcUriComponentsBuilder.controller(ItemController.class);
            controller.showItem("books", "42");

            String path = MvcUriComponentsBuilder
                    .fromMethodCall(UriComponentsBuilder.fromPath("/api"), controller)
                    .build()
                    .toUriString();

            assertThat(path).isEqualTo("/api/items/books/42");
        } catch (ExceptionInInitializerError error) {
            if (!hasUnsupportedFeatureErrorCause(error)) {
                throw error;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            restoreObjenesisIgnoreProperty(previousValue);
        }
    }

    private static boolean hasUnsupportedFeatureErrorCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void restoreObjenesisIgnoreProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME);
        } else {
            System.setProperty(SpringObjenesis.IGNORE_OBJENESIS_PROPERTY_NAME, previousValue);
        }
    }

    @RequestMapping("/items")
    public static class ItemController {
        @RequestMapping("/{category}/{id}")
        public void showItem(@PathVariable("category") String category, @PathVariable("id") String id) {
        }
    }
}
