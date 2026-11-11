/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler;

import static org.assertj.core.api.Assertions.assertThat;

public class NoSuchMethodFailureAnalyzerTest {
    @Test
    void diagnosesAnIncompatibleMethodUsingItsCallerAndTargetLocations() {
        ArgumentResolver arguments = ArgumentResolver.of(BeanFactory.class, new DefaultListableBeanFactory())
                .and(Environment.class, new StandardEnvironment());
        FailureAnalyzer analyzer = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
                .load(FailureAnalyzer.class, arguments, FailureHandler.logging(LogFactory.getLog(getClass())))
                .stream().filter(candidate -> candidate.getClass().getSimpleName().equals("NoSuchMethodFailureAnalyzer"))
                .findFirst().orElseThrow();
        NoSuchMethodError failure = new NoSuchMethodError("'void " + Binder.class.getName() + ".configureOrders()'");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(SpringApplication.class.getName(), "run", "SpringApplication.java", 1)
        });

        FailureAnalysis analysis = analyzer.analyze(failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getCause()).isSameAs(failure);
        assertThat(analysis.getDescription()).contains("configureOrders()", SpringApplication.class.getName(),
                Binder.class.getName(), "available from the following locations:");
        assertThat(analysis.getAction()).contains("compatible versions", SpringApplication.class.getName(),
                Binder.class.getName());
    }
}
