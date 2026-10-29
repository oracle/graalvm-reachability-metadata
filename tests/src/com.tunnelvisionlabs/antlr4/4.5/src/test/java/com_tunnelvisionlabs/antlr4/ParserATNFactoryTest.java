/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.LexerInterpreter;
import org.antlr.v4.runtime.ParserInterpreter;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.tool.Grammar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class ParserATNFactoryTest {
    @Test
    void buildsAndInterpretsOptionalAlternativeAndLoopStates() {
        Grammar grammar = GrammarTestSupport.process("""
                grammar Entries;
                start: ('!' | '?')? entry (',' entry)* ';' EOF;
                entry: ID+ | '(' ID ')';
                ID: [a-z]+;
                WS: [ \\t\\r\\n]+ -> skip;
                """);

        assertEquals(2, grammar.atn.ruleToStartState.length);
        assertEquals(2, grammar.atn.ruleToStopState.length);
        assertTrue(grammar.atn.getNumberOfDecisions() >= 4);
        assertEquals(grammar.atn.ruleToStopState[0], grammar.atn.ruleToStartState[0].stopState);

        assertParses(grammar, "!one two,(three),four;", "!onetwo,(three),four;<EOF>");
        assertParses(grammar, "single;", "single;<EOF>");
        assertParses(grammar, "?(only);", "?(only);<EOF>");
    }

    private static void assertParses(Grammar grammar, String input, String expectedText) {
        LexerInterpreter lexer = grammar.createLexerInterpreter(new ANTLRInputStream(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ParserInterpreter parser = grammar.createParserInterpreter(tokens);
        ParserRuleContext tree = parser.parse(grammar.getRule("start").index);

        assertEquals(0, parser.getNumberOfSyntaxErrors());
        assertEquals(expectedText, tree.getText());
        assertEquals(Token.EOF, tokens.LA(1));
        assertTrue(tree.toStringTree(parser).startsWith("(start "));
    }
}
