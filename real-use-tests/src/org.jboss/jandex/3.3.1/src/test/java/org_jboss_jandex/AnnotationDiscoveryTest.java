/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static java.lang.annotation.ElementType.TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jandex's annotation indexing subsystem to discover application job handlers.
public class AnnotationDiscoveryTest {
    @Test
    @Timeout(60)
    @SuppressWarnings("checkstyle:annotationAccess")
    public void discoversAnnotatedHandlersAndTheirRegistrationKeys() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(InvoiceHandler.class);
        indexer.indexClass(ShipmentHandler.class);
        indexer.indexClass(UnregisteredHandler.class);
        Index index = indexer.complete();

        List<HandlerRegistration> registrations = new ArrayList<>();
        for (AnnotationInstance annotation : index.getAnnotations(DotName.createSimple(JobHandler.class))) {
            registrations.add(new HandlerRegistration(
                    annotation.target().asClass().name().toString(),
                    annotation.value("key").asString()));
        }
        registrations.sort(Comparator.comparing(HandlerRegistration::key));

        assertEquals(List.of(
                new HandlerRegistration(InvoiceHandler.class.getName(), "invoice"),
                new HandlerRegistration(ShipmentHandler.class.getName(), "shipment")), registrations);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(TYPE)
    private @interface JobHandler {
        String key();
    }

    private interface Handler {
        String handle();
    }

    @JobHandler(key = "invoice")
    private static final class InvoiceHandler implements Handler {
        @Override
        public String handle() {
            return "invoice";
        }
    }

    @JobHandler(key = "shipment")
    private static final class ShipmentHandler implements Handler {
        @Override
        public String handle() {
            return "shipment";
        }
    }

    private static final class UnregisteredHandler implements Handler {
        @Override
        public String handle() {
            return "unregistered";
        }
    }

    private record HandlerRegistration(String className, String key) {
    }
}
