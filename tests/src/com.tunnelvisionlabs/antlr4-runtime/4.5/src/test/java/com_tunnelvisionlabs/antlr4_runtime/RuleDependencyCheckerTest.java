/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tunnelvisionlabs.antlr4_runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.AnnotatedElement;
import java.util.List;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleDependencies;
import org.antlr.v4.runtime.RuleDependency;
import org.antlr.v4.runtime.RuleVersion;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNSimulator;
import org.antlr.v4.runtime.misc.RuleDependencyChecker;
import org.antlr.v4.runtime.misc.Tuple2;
import org.junit.jupiter.api.Test;

public class RuleDependencyCheckerTest {
    @Test
    void discoversDependenciesOnSupportedMemberTypes() {
        List<Tuple2<RuleDependency, AnnotatedElement>> dependencies =
                RuleDependencyChecker.getDependencies(AnnotatedDependencyFixture.class);

        List<Integer> versions = dependencies.stream()
                .map(dependency -> dependency.getItem1().version())
                .collect(Collectors.toList());

        assertThat(dependencies).hasSize(5);
        assertThat(versions).containsExactlyInAnyOrder(2, 2, 7, 7, 7);
    }

    @Test
    void validatesRuleDependenciesAgainstRecognizerRuleVersions() {
        RuleDependencyChecker.checkDependencies(ValidDependencyFixture.class);
    }

    @Test
    void reportsMismatchedRuleVersion() {
        assertThatThrownBy(() -> RuleDependencyChecker.checkDependencies(InvalidDependencyFixture.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("firstRule@3")
                .hasMessageContaining("found @7");
    }

    @RuleDependency(recognizer = FixtureRecognizer.class, rule = FixtureRecognizer.RULE_firstRule, version = 7)
    public static class AnnotatedDependencyFixture {
        @RuleDependency(recognizer = FixtureRecognizer.class, rule = FixtureRecognizer.RULE_secondRule, version = 2)
        public String dependentField = "field";

        @RuleDependencies({
                @RuleDependency(
                        recognizer = FixtureRecognizer.class,
                        rule = FixtureRecognizer.RULE_firstRule,
                        version = 7),
                @RuleDependency(
                        recognizer = FixtureRecognizer.class,
                        rule = FixtureRecognizer.RULE_secondRule,
                        version = 2)
        })
        public AnnotatedDependencyFixture() {
        }

        @RuleDependency(recognizer = FixtureRecognizer.class, rule = FixtureRecognizer.RULE_firstRule, version = 7)
        public void dependentMethod() {
        }
    }

    @RuleDependency(recognizer = FixtureRecognizer.class, rule = FixtureRecognizer.RULE_firstRule, version = 7)
    public static class ValidDependencyFixture {
        @RuleDependency(recognizer = FixtureRecognizer.class, rule = FixtureRecognizer.RULE_secondRule, version = 2)
        public static class InnerDependency {
        }
    }

    @RuleDependency(recognizer = FixtureRecognizer.class, rule = FixtureRecognizer.RULE_firstRule, version = 3)
    public static class InvalidDependencyFixture {
    }

    public static class FixtureRecognizer extends Recognizer<Token, ATNSimulator> {
        public static String[] ruleNames = { "firstRule", "secondRule" };
        // Checkstyle: stop constant name check
        public static final int RULE_firstRule = 0;
        public static final int RULE_secondRule = 1;
        // Checkstyle: resume constant name check

        @Override
        public String[] getTokenNames() {
            return new String[0];
        }

        @Override
        public String[] getRuleNames() {
            return ruleNames;
        }

        @Override
        public String getGrammarFileName() {
            return "Fixture";
        }

        @Override
        public IntStream getInputStream() {
            return null;
        }

        @RuleVersion(7)
        public void firstRule() {
        }

        @RuleVersion(2)
        public void secondRule() {
        }
    }
}
