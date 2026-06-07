/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.boot.SpringBootExceptionReporter;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;

import static org.assertj.core.api.Assertions.assertThat;

public class NoSuchMethodFailureAnalyzerTest {

    @Test
    void springBootExceptionReporterAnalyzesNoSuchMethodError() {
        NoSuchMethodError failure = FailingCaller.createFailure();
        List<SpringBootExceptionReporter> reporters = SpringFactoriesLoader
            .forDefaultResourceLocation(getClass().getClassLoader())
            .load(SpringBootExceptionReporter.class, ArgumentResolver.none());

        assertThat(reporters).anySatisfy((reporter) -> assertThat(reporter.reportException(failure)).isTrue());
    }

    static final class FailingCaller {

        private FailingCaller() {
        }

        static NoSuchMethodError createFailure() {
            return new NoSuchMethodError("org.springframework.boot.SpringApplication.methodThatDoesNotExist()V");
        }

    }

}
