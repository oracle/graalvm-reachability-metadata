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
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jandex's sealed hierarchy metadata subsystem to discover application command variants.
public class SealedTypeDiscoveryTest {
    @Test
    @Timeout(60)
    public void discoversPermittedCommandVariants() throws IOException {
        Indexer indexer = new Indexer();
        indexer.indexClass(Command.class);
        indexer.indexClass(CreateInvoice.class);
        indexer.indexClass(CancelInvoice.class);
        Index index = indexer.complete();

        ClassInfo command = index.getClassByName(DotName.createSimple(Command.class));

        assertTrue(command.isSealed());
        assertEquals(List.of(CancelInvoice.class.getName(), CreateInvoice.class.getName()),
                command.permittedSubclasses().stream()
                        .map(DotName::toString)
                        .sorted()
                        .toList());
    }

    private sealed interface Command permits CreateInvoice, CancelInvoice {
    }

    private record CreateInvoice(String customerId) implements Command {
    }

    private record CancelInvoice(String invoiceId) implements Command {
    }
}
