/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.Tool;
import groovyjarjarantlr4.v4.parse.GrammarTreeVisitor;
import groovyjarjarantlr4.v4.tool.ast.TerminalAST;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GrammarTreeVisitorTest {
    @Test
    void visitsTheTokenReferencesInAParserGrammar() {
        List<String> references = new ArrayList<>();
        GrammarTreeVisitor visitor = new GrammarTreeVisitor() {
            @Override
            public void tokenRef(TerminalAST reference) {
                references.add(reference.getText());
            }
        };
        visitor.visitGrammar(new Tool().parseGrammarFromString("parser grammar Names; entry: NAME EOF;"));
        assertEquals(List.of("NAME", "EOF"), references);
    }
}
