/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.codegen.CodeGenerator;
import groovyjarjarantlr4.v4.tool.Grammar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActionTranslatorTest {
    @Test
    void translatesTokenAndRulePropertiesIntoParserSource() throws Exception {
        Grammar grammar = new Grammar("""
                grammar Labels;
                entry returns [String value]
                    : child=word EOF { $value = $child.text + $text + $start.getText(); }
                    ;
                word returns [String value]
                    : token=ID { $value = $token.text; }
                    ;
                ID: [a-z]+;
                """);
        String parser = new CodeGenerator(grammar).generateParser().render();
        assertEquals(0, grammar.tool.getNumErrors());
        assertTrue(parser.contains("getText()"));
        assertTrue(parser.contains("_input.getText("));
        assertTrue(parser.contains(".start"));
        assertTrue(parser.contains("class EntryContext"));
    }
}
