/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_resource.connector_api;

import java.io.IOException;

import javax.resource.ResourceException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ResourceExceptionTest {
    @Test
    void includesVendorErrorCodeInDiagnosticMessage() {
        ResourceException exception = new ResourceException("Connection unavailable", "EIS-OFFLINE");

        assertEquals("EIS-OFFLINE", exception.getErrorCode());
        assertEquals("Connection unavailable, error code: EIS-OFFLINE", exception.getMessage());
        exception.setErrorCode("EIS-BUSY");
        assertEquals("Connection unavailable, error code: EIS-BUSY", exception.getMessage());
        exception.setErrorCode(null);
        assertEquals("Connection unavailable", exception.getMessage());
    }

    @Test
    void reportsAnErrorCodeWithoutADetailMessage() {
        ResourceException exception = new ResourceException();

        assertNull(exception.getMessage());
        assertNull(exception.getErrorCode());
        exception.setErrorCode("EIS-TIMEOUT");
        assertEquals("error code: EIS-TIMEOUT", exception.getMessage());
    }

    @Test
    void preservesFailureCause() {
        IOException cause = new IOException("Connection reset");
        ResourceException exception = new ResourceException("Read failed", cause);

        assertEquals("Read failed", exception.getMessage());
        assertSame(cause, exception.getCause());
        ResourceException causeOnly = new ResourceException(cause);
        assertSame(cause, causeOnly.getCause());
        assertEquals(cause.toString(), causeOnly.getMessage());
    }

    @Test
    void retainsLegacyLinkedExceptionIndependentlyOfCause() {
        IOException cause = new IOException("Connection reset");
        IOException linked = new IOException("Adapter diagnostic");
        ResourceException exception = new ResourceException("Read failed", cause);

        assertNull(exception.getLinkedException());
        exception.setLinkedException(linked);
        assertSame(linked, exception.getLinkedException());
        assertSame(cause, exception.getCause());
    }
}
