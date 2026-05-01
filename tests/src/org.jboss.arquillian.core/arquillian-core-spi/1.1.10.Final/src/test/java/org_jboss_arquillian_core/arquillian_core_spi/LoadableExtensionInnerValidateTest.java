/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.arquillian.core.spi.LoadableExtension;
import org.junit.jupiter.api.Test;

public class LoadableExtensionInnerValidateTest {

    @Test
    void classExistsReturnsTrueForAvailableClass() {
        boolean exists = LoadableExtension.Validate.classExists(String.class.getName());

        assertThat(exists).isTrue();
    }

    @Test
    void classExistsReturnsFalseForUnavailableClass() {
        boolean exists = LoadableExtension.Validate.classExists("org.example.DoesNotExist");

        assertThat(exists).isFalse();
    }
}
