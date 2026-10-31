/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovyjarjarantlr4.v4.runtime.RuleDependency;
import groovyjarjarantlr4.v4.runtime.misc.RuleDependencyChecker;
import org.apache.groovy.parser.antlr4.GroovyParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RuleDependencyCheckerTest {
    @Test
    void discoversDependenciesOnConstructorsFieldsAndMethods() {
        var dependencies = RuleDependencyChecker.getDependencies(ParserConsumer.class);
        assertEquals(3, dependencies.size());
        assertTrue(dependencies.stream().allMatch(dependency -> dependency.getItem1().recognizer() == GroovyParser.class));
        assertTrue(dependencies.stream().allMatch(dependency -> dependency.getItem1().rule() == GroovyParser.RULE_compilationUnit));
    }

    public static class ParserConsumer {
        @RuleDependency(recognizer = GroovyParser.class, rule = GroovyParser.RULE_compilationUnit, version = 0)
        public String source;

        @RuleDependency(recognizer = GroovyParser.class, rule = GroovyParser.RULE_compilationUnit, version = 0)
        public ParserConsumer() {
        }

        @RuleDependency(recognizer = GroovyParser.class, rule = GroovyParser.RULE_compilationUnit, version = 0)
        public void read() {
        }
    }
}
