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
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jandex's enum metadata subsystem to build an application status selector.
public class EnumSchemaDiscoveryTest {
    @Test
    @Timeout(60)
    public void discoversStatusChoicesAndTheirDeclarationOrder() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(DeliveryStatus.class);
        Index index = indexer.complete();
        ClassInfo statusType = index.getClassByName(DotName.createSimple(DeliveryStatus.class));

        List<StatusChoice> choices = statusType.enumConstants().stream()
                .map(constant -> new StatusChoice(constant.name(), constant.enumConstantOrdinal()))
                .toList();

        assertTrue(statusType.isEnum());
        assertEquals(List.of(
                new StatusChoice("QUEUED", 0),
                new StatusChoice("IN_TRANSIT", 1),
                new StatusChoice("DELIVERED", 2),
                new StatusChoice("CANCELLED", 3)), choices);
        assertEquals(List.of("QUEUED", "IN_TRANSIT", "DELIVERED", "CANCELLED"),
                statusType.enumConstants().stream().map(FieldInfo::name).toList());
    }

    private enum DeliveryStatus {
        QUEUED,
        IN_TRANSIT,
        DELIVERED,
        CANCELLED
    }

    private record StatusChoice(String name, int ordinal) {
    }
}
