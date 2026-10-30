/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_bsf.bsf_api;

import java.io.IOException;

import javax.script.ScriptException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScriptExceptionTest {
    @Test
    void preservesAMessageWithoutSourceLocation() {
        ScriptException exception = new ScriptException("unknown variable");
        assertEquals("unknown variable", exception.getMessage());
        assertNull(exception.getFileName());
        assertEquals(-1, exception.getLineNumber());
        assertEquals(-1, exception.getColumnNumber());
    }

    @Test
    void includesFileAndLineInDiagnostics() {
        ScriptException exception = new ScriptException("unexpected token", "invoice.script", 12);
        assertEquals("invoice.script", exception.getFileName());
        assertEquals(12, exception.getLineNumber());
        assertEquals(-1, exception.getColumnNumber());
        assertTrue(exception.getMessage().contains("unexpected token"));
        assertTrue(exception.getMessage().contains("invoice.script"));
        assertTrue(exception.getMessage().contains("12"));
    }

    @Test
    void includesColumnInDiagnostics() {
        ScriptException exception = new ScriptException("missing operand", "invoice.script", 12, 7);
        assertEquals("invoice.script", exception.getFileName());
        assertEquals(12, exception.getLineNumber());
        assertEquals(7, exception.getColumnNumber());
        assertTrue(exception.getMessage().contains("missing operand"));
        assertTrue(exception.getMessage().contains("invoice.script"));
        assertTrue(exception.getMessage().contains("12"));
        assertTrue(exception.getMessage().contains("7"));
    }

    @Test
    void retainsTheUnderlyingException() {
        IOException cause = new IOException("script input unavailable");
        ScriptException exception = new ScriptException(cause);
        assertSame(cause, exception.getCause());
        assertTrue(exception.getMessage().contains("script input unavailable"));
        assertNull(exception.getFileName());
        assertEquals(-1, exception.getLineNumber());
        assertEquals(-1, exception.getColumnNumber());
    }
}
