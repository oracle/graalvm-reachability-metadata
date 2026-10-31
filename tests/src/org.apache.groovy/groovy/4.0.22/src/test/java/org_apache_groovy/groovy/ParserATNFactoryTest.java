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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParserATNFactoryTest {
    @Test
    void buildsAnAutomatonAndInterpretsAListGrammarWithoutDefiningClasses() throws Exception {
        Grammar grammar = new Grammar("grammar Entries; entries: ID (',' ID)* EOF; ID: [a-z]+;");
        CommonTokenStream tokens = new CommonTokenStream(grammar.createLexerInterpreter(CharStreams.fromString("ada,lin")));
        ParserInterpreter parser = grammar.createParserInterpreter(tokens);
        assertEquals(String.join(" ", "(entries", "ada", ",", "lin", "<EOF>)"),
                parser.parse(grammar.getRule("entries").index).toStringTree(parser));
        assertEquals(0, parser.getNumberOfSyntaxErrors());
    }
}
