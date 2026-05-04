/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_spi;

import org.jboss.arquillian.core.spi.LoadableExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoadableExtensionInnerValidateTest {
    @Test
    void reportsExistingClassAsAvailable() {
        boolean exists = LoadableExtension.Validate.classExists(LoadableExtension.class.getName());

        assertThat(exists).isTrue();
    }

    @Test
    void reportsMissingClassAsUnavailable() {
        boolean exists = LoadableExtension.Validate.classExists("org.jboss.arquillian.core.spi.DoesNotExist");

        assertThat(exists).isFalse();
    }
}
