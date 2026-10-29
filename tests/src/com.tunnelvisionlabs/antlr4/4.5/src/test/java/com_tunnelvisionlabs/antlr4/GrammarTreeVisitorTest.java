/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import org.antlr.v4.tool.Grammar;
import org.antlr.v4.tool.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class GrammarTreeVisitorTest {
    @Test
    void collectsRulesTokensOptionsAndActionsDuringSemanticProcessing() {
        Grammar grammar = GrammarTestSupport.process("""
                parser grammar Inventory;
                options { language=Java; }
                tokens { ID, COMMA }
                @members { private int itemCount; }
                list returns [int size]: items+=item (COMMA items+=item)*;
                item: ID;
                """);

        assertArrayEquals(new String[]{"list", "item"}, grammar.getRuleNames());
        assertEquals("Java", grammar.getOptionString("language"));
        assertTrue(grammar.getTokenType("ID") > 0);
        assertTrue(grammar.getTokenType("COMMA") > 0);
        assertTrue(grammar.namedActions.containsKey("members"));
        assertTrue(grammar.namedActions.get("members").getText().contains("private int itemCount;"));
        Rule list = grammar.getRule("list");
        assertNotNull(list.retvals.get("size"));
        assertEquals("int", list.retvals.get("size").type);
        assertEquals(0, list.index);
        assertEquals(1, grammar.getRule("item").index);
        assertEquals(0, grammar.tool.getNumErrors());
    }
}
