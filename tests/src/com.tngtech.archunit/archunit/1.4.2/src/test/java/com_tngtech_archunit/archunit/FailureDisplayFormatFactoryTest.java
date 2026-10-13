/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_tngtech_archunit.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.FailureDisplayFormat;
import com.tngtech.archunit.lang.FailureMessages;
import com.tngtech.archunit.lang.Priority;
import org.junit.jupiter.api.Test;

public class FailureDisplayFormatFactoryTest {
    @Test
    void failureReportUsesConfiguredDisplayFormat() {
        ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.setProperty("failureDisplayFormat", CompactFailureFormat.class.getName());
            JavaClasses classesToCheck =
                    new ClassFileImporter().importClasses(AnnotatedFixture.class);

            String report = classes()
                    .should()
                    .haveSimpleName("DifferentName")
                    .evaluate(classesToCheck)
                    .getFailureReport()
                    .toString();

            assertThat(report).isEqualTo("formatted:1");
        });
    }

    public static final class CompactFailureFormat implements FailureDisplayFormat {
        public CompactFailureFormat() {}

        @Override
        public String formatFailure(
                HasDescription rule, FailureMessages failureMessages, Priority priority) {
            return "formatted:" + failureMessages.size();
        }
    }
}
