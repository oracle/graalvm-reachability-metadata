/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalatest.scalatest_compatible;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.scalatest.compatible.Assertion;

public class Scalatest_compatibleTest {
    @Test
    void assertionCanBeImplementedByMultipleJavaTypeKinds() {
        NamedAssertion recordAssertion = new NamedAssertion("record", true);
        OutcomeAssertion enumAssertion = OutcomeAssertion.SUCCEEDED;
        Assertion anonymousAssertion = new Assertion() {
            @Override
            public String toString() {
                return "anonymous";
            }
        };

        assertThat(roundTrip(recordAssertion)).isEqualTo(recordAssertion);
        assertThat(roundTrip(enumAssertion)).isSameAs(enumAssertion);
        assertThat(roundTrip(anonymousAssertion)).isSameAs(anonymousAssertion);

        assertThat(List.<Assertion>of(recordAssertion, enumAssertion, anonymousAssertion))
                .containsExactly(recordAssertion, enumAssertion, anonymousAssertion);
        assertThat(describe(recordAssertion)).isEqualTo("record:passed");
        assertThat(describe(enumAssertion)).isEqualTo("enum:SUCCEEDED");
        assertThat(describe(anonymousAssertion)).isEqualTo("anonymous");
    }

    @Test
    void assertionCanFlowThroughGenericCollectionAndMethodContracts() {
        NamedAssertion first = new NamedAssertion("first", true);
        NamedAssertion second = new NamedAssertion("second", false);
        AssertionBox<NamedAssertion> box = new AssertionBox<>(first);
        List<Assertion> assertions = List.of(first, second, OutcomeAssertion.SUCCEEDED);

        assertThat(box.value()).isEqualTo(first);
        assertThat(successfulAssertions(assertions))
                .containsExactly(first, OutcomeAssertion.SUCCEEDED);
        assertThat(joinDescriptions(assertions))
                .isEqualTo("first:passed,second:failed,enum:SUCCEEDED");
    }

    @Test
    void assertionCanBeSpecializedThroughCustomSubInterfaces() {
        Assertion passing = evaluateDiagnostic(true, "all checks passed");
        Assertion failing = evaluateDiagnostic(false, "missing expected field");

        assertThat(passing).isInstanceOf(DiagnosticAssertion.class);
        assertThat(failing).isInstanceOf(DiagnosticAssertion.class);
        assertThat(renderDiagnostic(passing)).isEqualTo("PASS: all checks passed");
        assertThat(renderDiagnostic(failing)).isEqualTo("FAIL: missing expected field");
    }

    private static String joinDescriptions(List<? extends Assertion> assertions) {
        return assertions.stream()
                .map(Scalatest_compatibleTest::describe)
                .collect(Collectors.joining(","));
    }

    private static List<Assertion> successfulAssertions(List<? extends Assertion> assertions) {
        return assertions.stream()
                .filter(Scalatest_compatibleTest::isSuccessful)
                .map(assertion -> (Assertion) assertion)
                .toList();
    }

    private static boolean isSuccessful(Assertion assertion) {
        if (assertion instanceof NamedAssertion namedAssertion) {
            return namedAssertion.passed();
        }
        return assertion == OutcomeAssertion.SUCCEEDED;
    }

    private static String describe(Assertion assertion) {
        if (assertion instanceof NamedAssertion namedAssertion) {
            return namedAssertion.name() + ":" + (namedAssertion.passed() ? "passed" : "failed");
        }
        if (assertion instanceof OutcomeAssertion outcomeAssertion) {
            return "enum:" + outcomeAssertion.name();
        }
        return assertion.toString();
    }

    private static <T extends Assertion> T roundTrip(T assertion) {
        return assertion;
    }

    private static Assertion evaluateDiagnostic(boolean passed, String detail) {
        return new DiagnosticResult(passed, detail);
    }

    private static String renderDiagnostic(Assertion assertion) {
        if (assertion instanceof DiagnosticAssertion diagnosticAssertion) {
            return (diagnosticAssertion.passed() ? "PASS" : "FAIL") + ": " + diagnosticAssertion.detail();
        }
        return describe(assertion);
    }

    private record NamedAssertion(String name, boolean passed) implements Assertion {
    }

    private interface DiagnosticAssertion extends Assertion {
        boolean passed();

        String detail();
    }

    private record DiagnosticResult(boolean passed, String detail) implements DiagnosticAssertion {
    }

    private enum OutcomeAssertion implements Assertion {
        SUCCEEDED,
        FAILED
    }

    private record AssertionBox<T extends Assertion>(T value) {
    }
}
