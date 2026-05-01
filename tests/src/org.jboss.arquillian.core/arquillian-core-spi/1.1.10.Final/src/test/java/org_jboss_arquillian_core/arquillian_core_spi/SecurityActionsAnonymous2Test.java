/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.arquillian.core.spi.SecurityActionsInvoker;
import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous2Test {

    @Test
    void setFieldValueUpdatesPrivateField() {
        String value = SecurityActionsInvoker.setPrivateFieldValue("original", "updated");

        assertThat(value).isEqualTo("updated");
    }
}
