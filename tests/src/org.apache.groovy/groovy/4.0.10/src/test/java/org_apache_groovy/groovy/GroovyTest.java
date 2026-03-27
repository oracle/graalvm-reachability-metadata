/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroovyTest {
    @Test
    void missingMethodExceptionExposesMethodMetadata() {
        Object[] arguments = {1, "two"};
        MissingMethodException exception =
                new MissingMethodException("runTask", GroovyTest.class, arguments, false);

        assertThat(exception.getMethod()).isEqualTo("runTask");
        assertThat(exception.getType()).isEqualTo(GroovyTest.class);
        assertThat(exception.getArguments()).containsExactly(1, "two");
    }

    @Test
    void missingPropertyExceptionExposesPropertyMetadata() {
        MissingPropertyException exception = new MissingPropertyException("name", GroovyTest.class);

        assertThat(exception.getProperty()).isEqualTo("name");
        assertThat(exception.getType()).isEqualTo(GroovyTest.class);
    }
}
