/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationOverlay;
import org.jboss.jandex.AnnotationTransformation;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Jandex's annotation overlay subsystem to apply framework security metadata to application handlers.
public class AnnotationOverlayTest {
    @Test
    @Timeout(60)
    public void addsSyntheticSecurityMetadataWithoutChangingTheIndex() throws IOException {
        Index index = Index.of(OrderResource.class);
        ClassInfo resource = index.getClassByName(OrderResource.class);
        MethodInfo getOrder = resource.firstMethod("getOrder");
        MethodInfo health = resource.firstMethod("health");

        AnnotationTransformation securityTransformation = AnnotationTransformation.forMethods()
                .whenMethod(OrderResource.class, "getOrder")
                .transform(context -> context.add(RequiresAuthentication.class));
        AnnotationOverlay overlay = AnnotationOverlay.builder(index, List.of(securityTransformation)).build();

        assertFalse(getOrder.hasAnnotation(RequiresAuthentication.class));
        assertFalse(health.hasAnnotation(RequiresAuthentication.class));
        assertTrue(overlay.hasAnnotation(getOrder, RequiresAuthentication.class));
        assertFalse(overlay.hasAnnotation(health, RequiresAuthentication.class));

        AnnotationInstance security = overlay.annotation(getOrder, RequiresAuthentication.class);
        assertNotNull(security);
        assertEquals(RequiresAuthentication.class.getName(), security.name().toString());
        assertEquals(List.of(security), List.copyOf(overlay.annotations(getOrder).stream()
                .filter(annotation -> annotation.name().equals(security.name()))
                .toList()));
    }

    private static final class OrderResource {
        public String getOrder(String orderId) {
            return "order-" + orderId;
        }

        public String health() {
            return "ok";
        }
    }

    private @interface RequiresAuthentication {
    }
}
