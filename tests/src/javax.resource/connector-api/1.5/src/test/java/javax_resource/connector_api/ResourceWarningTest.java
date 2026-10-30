/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_resource.connector_api;

import java.io.IOException;

import javax.resource.cci.ResourceWarning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ResourceWarningTest {
    @Test
    void traversesWarningsFromAnInteraction() {
        ResourceWarning first = new ResourceWarning("Result truncated", "ROW-LIMIT");
        ResourceWarning second = new ResourceWarning("Value rounded");
        ResourceWarning third = new ResourceWarning("Optional field absent");
        first.setLinkedWarning(second);
        second.setLinkedWarning(third);

        assertEquals("Result truncated, error code: ROW-LIMIT", first.getMessage());
        assertEquals("ROW-LIMIT", first.getErrorCode());
        assertSame(second, first.getLinkedWarning());
        assertSame(third, first.getLinkedWarning().getLinkedWarning());
        assertNull(third.getLinkedWarning());
    }

    @Test
    void preservesTheUnderlyingWarningCause() {
        IOException cause = new IOException("Partial response");
        ResourceWarning warning = new ResourceWarning("Incomplete result", cause);

        assertEquals("Incomplete result", warning.getMessage());
        assertSame(cause, warning.getCause());
        assertNull(warning.getLinkedWarning());
    }
}
