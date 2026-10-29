/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.tool.Grammar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(120)
public class Antlr4Test {
    @Test
    void tokenizesIdentifiersAndNumbersWithAnInterpretedLexer() {
        Grammar grammar = GrammarTestSupport.process("""
                lexer grammar Fields;
                ID: [a-z]+;
                INT: [0-9]+;
                WS: [ \\t\\r\\n]+ -> skip;
                """);
        CommonTokenStream tokens = new CommonTokenStream(
                grammar.createLexerInterpreter(new ANTLRInputStream("quantity 42")));
        tokens.fill();

        assertEquals(3, tokens.size());
        assertEquals(grammar.getTokenType("ID"), tokens.get(0).getType());
        assertEquals("quantity", tokens.get(0).getText());
        assertEquals(grammar.getTokenType("INT"), tokens.get(1).getType());
        assertEquals("42", tokens.get(1).getText());
        assertEquals(Token.EOF, tokens.get(2).getType());
    }
}
