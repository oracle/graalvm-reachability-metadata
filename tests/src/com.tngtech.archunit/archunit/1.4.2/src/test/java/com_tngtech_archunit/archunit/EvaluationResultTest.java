/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

public class EvaluationResultTest {
    @Test
    void evaluationLoadsIgnorePatternsBeforeReportingViolations() {
        JavaClasses importedClasses = TestClassImporter.importClasses(AnnotatedFixture.class);

        EvaluationResult result = classes()
                .should()
                .haveSimpleName("DifferentName")
                .evaluate(importedClasses);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains("AnnotatedFixture"));
    }
}
