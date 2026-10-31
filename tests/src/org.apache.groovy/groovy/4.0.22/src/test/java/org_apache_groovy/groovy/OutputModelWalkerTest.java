/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.Tool;
import groovyjarjarantlr4.v4.codegen.CodeGenerator;
import groovyjarjarantlr4.v4.tool.Grammar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OutputModelWalkerTest {
    @Test
    void rendersLexerRulesAndModesFromTheOutputModel() throws Exception {
        Tool tool = new Tool();
        Grammar grammar = tool.createGrammar(tool.parseGrammarFromString("""
                lexer grammar ParcelTokens;
                OPEN: '[' -> pushMode(LABEL);
                mode LABEL;
                TEXT: [a-z]+;
                CLOSE: ']' -> popMode;
                """));
        grammar.fileName = "ParcelTokens.g4";
        tool.process(grammar, false);
        String source = new CodeGenerator(grammar).generateLexer().render();
        assertTrue(source.contains("class ParcelTokens extends Lexer"), source);
        assertTrue(source.contains("\"OPEN\", \"TEXT\", \"CLOSE\""), source);
        assertTrue(source.contains("\"DEFAULT_MODE\", \"LABEL\""), source);
        assertEquals(0, grammar.tool.getNumErrors());
    }
}
