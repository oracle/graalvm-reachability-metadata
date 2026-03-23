/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.MissingPropertyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class GroovyTest {
    @Test
    void test() throws Exception {
        fail("TODO: Add test logic here");
    }

    @Test
    void missingPropertyExceptionExposesPropertyMetadataAndMessage() {
        MissingPropertyException exception = new MissingPropertyException("name", GroovyTest.class);

        assertThat(exception.getProperty()).isEqualTo("name");
        assertThat(exception.getType()).isEqualTo(GroovyTest.class);
        assertThat(exception.getMessage())
                .contains("name")
                .contains(GroovyTest.class.getName());
    }
}
