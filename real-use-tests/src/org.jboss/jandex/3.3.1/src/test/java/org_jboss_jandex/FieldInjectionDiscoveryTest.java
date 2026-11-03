/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jandex's field metadata subsystem to build an application configuration injection plan.
public class FieldInjectionDiscoveryTest {
    @Test
    @Timeout(60)
    public void discoversConfigurationFieldsAndTheirBindingNames() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(PaymentApplication.class);
        Index index = indexer.complete();
        ClassInfo application = index.getClassByName(DotName.createSimple(PaymentApplication.class));

        List<FieldBinding> bindings = application.fields().stream()
                .filter(field -> field.hasAnnotation(DotName.createSimple(ConfigValue.class)))
                .map(field -> new FieldBinding(
                        field.name(),
                        field.type().name().toString(),
                        field.annotation(DotName.createSimple(ConfigValue.class)).value("name").asString()))
                .sorted(Comparator.comparing(FieldBinding::fieldName))
                .toList();

        assertEquals(List.of(
                new FieldBinding("paymentRegion", String.class.getName(), "payments.region"),
                new FieldBinding("retryLimit", int.class.getName(), "payments.retry-limit")), bindings);
        assertTrue(application.field("auditEnabled").annotations().isEmpty());
    }

    private static final class PaymentApplication {
        @ConfigValue(name = "payments.region")
        private String paymentRegion;

        @ConfigValue(name = "payments.retry-limit")
        private int retryLimit;

        private boolean auditEnabled;
    }

    private @interface ConfigValue {
        String name();
    }

    private record FieldBinding(String fieldName, String fieldType, String configurationName) {
    }
}
