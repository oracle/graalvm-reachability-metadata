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

public class CodeGeneratorTest {
    @Test
    void generatesJavaParserSourceForANamedRule() throws Exception {
        Tool tool = new Tool();
        Grammar grammar = tool.createGrammar(tool.parseGrammarFromString(
                "grammar Shipment; shipment: ID EOF; ID: [a-z]+;"));
        grammar.fileName = "Shipment.g4";
        tool.process(grammar, false);
        CodeGenerator generator = new CodeGenerator(grammar);
        String source = generator.generateParser().render();
        assertTrue(source.contains("class ShipmentParser extends Parser"), source);
        assertTrue(source.contains("ShipmentContext shipment()"), source);
        assertTrue(source.contains("match(ID)"), source);
        assertEquals(0, grammar.tool.getNumErrors());
    }
}
