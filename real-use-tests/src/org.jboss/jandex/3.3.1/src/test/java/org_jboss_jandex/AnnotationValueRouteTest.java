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
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Jandex's annotation value subsystem to build an application route descriptor.
public class AnnotationValueRouteTest {
    @Test
    @Timeout(60)
    public void readsRouteConfigurationForApplicationDispatch() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(RouteHandler.class);
        indexer.indexClass(Route.class);
        indexer.indexClass(Security.class);
        Index index = indexer.complete();

        ClassInfo handler = index.getClassByName(DotName.createSimple(RouteHandler.class));
        MethodInfo submit = handler.firstMethod("submit");
        AnnotationInstance route = submit.annotation(DotName.createSimple(Route.class));
        assertNotNull(route);

        assertEquals("/orders", route.value("path").asString());
        assertEquals("POST", route.value("method").asEnum());
        assertEquals(HttpMethod.class.getName(), route.value("method").asEnumType().toString());
        assertEquals(List.of("application/json", "application/problem+json"),
                List.of(route.value("produces").asStringArray()));
        assertEquals(List.of(OrderRequest.class.getName(), OrderResponse.class.getName()),
                List.of(route.value("bodyTypes").asClassArray()).stream()
                        .map(type -> type.name().toString())
                        .toList());

        AnnotationInstance security = route.value("security").asNested();
        assertEquals("operator", security.value("role").asString());
        assertEquals(List.of("orders:read", "orders:write"),
                List.of(security.value("scopes").asStringArray()));
    }

    private static final class RouteHandler {
        @Route(
                path = "/orders",
                method = HttpMethod.POST,
                produces = { "application/json", "application/problem+json" },
                bodyTypes = { OrderRequest.class, OrderResponse.class },
                security = @Security(role = "operator", scopes = { "orders:read", "orders:write" }))
        public OrderResponse submit(OrderRequest request) {
            return new OrderResponse(request.id());
        }
    }

    private static final class OrderRequest {
        String id() {
            return "order-42";
        }
    }

    private static final class OrderResponse {
        OrderResponse(String id) {
        }
    }

    private enum HttpMethod {
        GET,
        POST
    }

    private @interface Route {
        String path();

        HttpMethod method();

        String[] produces();

        Class<?>[] bodyTypes();

        Security security();
    }

    private @interface Security {
        String role();

        String[] scopes();
    }
}
