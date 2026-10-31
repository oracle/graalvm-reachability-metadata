/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.runtime.CharStreams;
import groovyjarjarantlr4.v4.runtime.CommonTokenStream;
import groovyjarjarantlr4.v4.runtime.ParserInterpreter;
import groovyjarjarantlr4.v4.tool.Grammar;
import groovyjarjarantlr4.v4.tool.GrammarParserInterpreter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class GrammarParserInterpreterTest {
    @Test
    void derivesAnIndependentInterpreterForTheSameGrammar() throws Exception {
        Grammar grammar = new Grammar("grammar Word; entry: ID EOF; ID: [a-z]+;");
        CommonTokenStream tokens = new CommonTokenStream(grammar.createLexerInterpreter(CharStreams.fromString("parcel")));
        GrammarParserInterpreter original = grammar.createGrammarParserInterpreter(tokens);
        ParserInterpreter copy = GrammarParserInterpreter.deriveTempParserInterpreter(grammar, original, tokens);
        assertNotSame(original, copy);
        assertEquals("(entry:1 parcel <EOF>)", copy.parse(grammar.getRule("entry").index).toStringTree(copy));
        assertEquals(0, copy.getNumberOfSyntaxErrors());
    }
}
