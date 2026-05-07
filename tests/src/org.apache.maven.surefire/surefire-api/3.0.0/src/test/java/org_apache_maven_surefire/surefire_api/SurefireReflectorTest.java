/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.plugin.surefire.log.api.ConsoleLogger;
import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.surefire.booter.SurefireReflector;
import org.junit.jupiter.api.Test;

public class SurefireReflectorTest {

    @Test
    public void constructorLoadsProviderApiTypesAndConsoleLoggerDecorator() {
        final ClassLoader classLoader = SurefireReflectorTest.class.getClassLoader();
        final SurefireReflector reflector = new SurefireReflector(classLoader);
        final ConsoleLogger logger = new NullConsoleLogger();

        final Object decoratedLogger = reflector.createConsoleLogger(logger);

        assertThat(decoratedLogger).isInstanceOf(ConsoleLogger.class);
        assertThat(decoratedLogger).isNotSameAs(logger);
    }
}
