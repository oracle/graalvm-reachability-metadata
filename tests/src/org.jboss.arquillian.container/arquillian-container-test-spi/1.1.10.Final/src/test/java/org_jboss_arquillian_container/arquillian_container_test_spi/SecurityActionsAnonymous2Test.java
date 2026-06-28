/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.arquillian.container.test.spi.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous2Test {
    @Test
    void setFieldValueUpdatesPrivateFieldOnTargetObject() throws Exception {
        MutableHolder holder = new MutableHolder("initial");

        SecurityActions.setFieldValue(MutableHolder.class, holder, "value", "updated");

        assertThat(holder.value()).isEqualTo("updated");
    }

    private static final class MutableHolder {
        private String value;

        private MutableHolder(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
