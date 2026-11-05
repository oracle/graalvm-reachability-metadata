/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.List;

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
    void explainsMissingMethodAndIdentifiesTheIncompatibleClasses() {
        ArgumentResolver arguments = ArgumentResolver.of(BeanFactory.class, new DefaultListableBeanFactory())
                .and(Environment.class, new StandardEnvironment());
        List<FailureAnalyzer> analyzers = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
                .load(FailureAnalyzer.class, arguments, FailureHandler.logging(LogFactory.getLog(getClass())));
        NoSuchMethodError failure = new NoSuchMethodError("'void " + Binder.class.getName() + ".configureEndpoint()'");
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement(SpringApplication.class.getName(), "run", "SpringApplication.java", 1)
        });

        List<FailureAnalysis> analyses = analyzers.stream().map(analyzer -> analyzer.analyze(failure))
                .filter(analysis -> analysis != null).toList();

        assertThat(analyses).singleElement().satisfies(analysis -> {
            assertThat(analysis.getCause()).isSameAs(failure);
            assertThat(analysis.getDescription()).contains("configureEndpoint()", SpringApplication.class.getName(),
                    Binder.class.getName(), "available from the following locations:");
            assertThat(analysis.getAction()).contains("compatible versions", SpringApplication.class.getName(),
                    Binder.class.getName());
        });
    }
}
