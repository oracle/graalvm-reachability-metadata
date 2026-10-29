/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import org.antlr.runtime.CommonToken;
import org.antlr.v4.Tool;
import org.antlr.v4.tool.ANTLRMessage;
import org.antlr.v4.tool.ErrorManager;
import org.antlr.v4.tool.ErrorType;
import org.antlr.v4.tool.GrammarSemanticsMessage;
import org.antlr.v4.tool.ToolMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ErrorManagerTest {
    @Test
    void rendersDiagnosticsUsingBundledMessageFormats() {
        ErrorManager manager = new Tool().errMgr;
        CommonToken token = new CommonToken(0, "missing");
        token.setLine(4);
        token.setCharPositionInLine(7);
        ANTLRMessage message = new GrammarSemanticsMessage(
                ErrorType.UNDEFINED_RULE_REF, "Sample.g4", token, "missing");

        manager.setFormat("antlr");
        assertEquals("error(56): Sample.g4:4:7: reference to undefined rule: missing",
                manager.getMessageTemplate(message).render());
        assertFalse(manager.formatWantsSingleLineMessage());

        manager.setFormat("gnu");
        assertEquals("Sample.g4:4: error: reference to undefined rule: missing (56)",
                manager.getMessageTemplate(message).render());
        assertTrue(manager.formatWantsSingleLineMessage());

        manager.setFormat("vs2005");
        assertEquals("Sample.g4(4,7) : error 56 : reference to undefined rule: missing",
                manager.getMessageTemplate(message).render());
        assertTrue(manager.formatWantsSingleLineMessage());
    }

    @Test
    void fallsBackToDefaultFormatWhenRequestedFormatDoesNotExist() {
        ErrorManager manager = new Tool().errMgr;
        manager.setFormat("unavailable-message-format");
        String diagnostic = manager.getMessageTemplate(
                new ToolMessage(ErrorType.INVALID_CMDLINE_ARG, "-invalid")).render();

        assertFalse(manager.formatWantsSingleLineMessage());
        assertTrue(diagnostic.startsWith("error(2):"));
        assertTrue(diagnostic.contains("unknown command-line option '-invalid'"));
    }
}
