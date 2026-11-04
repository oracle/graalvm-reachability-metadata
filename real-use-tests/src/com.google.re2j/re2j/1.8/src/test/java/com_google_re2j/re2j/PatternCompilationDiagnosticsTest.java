/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_re2j.re2j;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the pattern compilation diagnostics subsystem to report invalid routing rules.
 */
public class PatternCompilationDiagnosticsTest {

    @Test
    @Timeout(60)
    void reportsTheConfiguredRuleThatCouldNotBeCompiled() {
        RuleValidator validator = new RuleValidator();

        ValidationResult result = validator.validate("route=(?<tenant>[a-z-]+");

        assertEquals("missing closing )", result.description());
        assertEquals("route=(?<tenant>[a-z-]+", result.pattern());
    }

    /** Application configuration validation that turns a regex syntax error into a report. */
    private static final class RuleValidator {

        ValidationResult validate(String expression) {
            try {
                Pattern.compile(expression);
                return new ValidationResult("valid", expression);
            } catch (PatternSyntaxException exception) {
                return new ValidationResult(exception.getDescription(), exception.getPattern());
            }
        }
    }

    /** The diagnostic a configuration service shows for one routing rule. */
    private record ValidationResult(String description, String pattern) {
    }
}
