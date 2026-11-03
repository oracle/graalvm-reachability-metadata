/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.IOException;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.RecordComponentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jandex's record metadata subsystem to derive an event schema for an application consumer.
public class RecordSchemaDiscoveryTest {
    @Test
    @Timeout(60)
    public void discoversRecordSchemaAndAccessors() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(OrderSubmitted.class);
        ClassInfo eventInfo = indexer.complete().getClassByName(DotName.createSimple(OrderSubmitted.class));

        assertTrue(eventInfo.isRecord());
        List<RecordComponentInfo> components = eventInfo.recordComponentsInDeclarationOrder();
        assertEquals(List.of("orderId:java.lang.String", "totalCents:long"), components.stream()
                .map(component -> component.name() + ":" + component.type().name())
                .toList());
        assertEquals(List.of("orderId", "totalCents"), components.stream()
                .map(component -> component.accessor().name())
                .toList());
        assertEquals(List.of("java.lang.String", "long"), components.stream()
                .map(component -> component.field().type().name().toString())
                .toList());
    }

    private record OrderSubmitted(String orderId, long totalCents) {
    }
}
