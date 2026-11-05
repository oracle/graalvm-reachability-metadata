/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jandex's method-parameter metadata subsystem to build application request bindings.
public class MethodParameterBindingTest {
    @Test
    @Timeout(60)
    public void discoversRequestBindingsAndAnnotatedParameterTypes() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(CheckoutResource.class);
        Index index = indexer.complete();

        ClassInfo resource = index.getClassByName(DotName.createSimple(CheckoutResource.class));
        MethodInfo submit = resource.firstMethod("submit");
        List<MethodParameterInfo> parameters = submit.parameters();

        assertEquals(2, parameters.size());
        assertEquals(List.of("orderId", "payload"), parameters.stream()
                .map(parameter -> parameter.annotation(Bind.class).value("name").asString())
                .toList());
        assertEquals(List.of(String.class.getName(), CheckoutRequest.class.getName()), parameters.stream()
                .map(parameter -> parameter.type().name().toString())
                .toList());
        assertTrue(parameters.get(1).type().hasAnnotation(DotName.createSimple(Sensitive.class)));
        assertEquals(List.of(0, 1), parameters.stream()
                .map(MethodParameterInfo::position)
                .map(Integer::valueOf)
                .toList());
    }

    private static final class CheckoutResource {
        public String submit(@Bind(name = "orderId") String orderId,
                @Bind(name = "payload") @Sensitive CheckoutRequest payload) {
            return orderId + ":" + payload.reference();
        }
    }

    private static final class CheckoutRequest {
        String reference() {
            return "checkout";
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface Bind {
        String name();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    private @interface Sensitive {
    }
}
