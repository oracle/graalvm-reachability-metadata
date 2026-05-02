/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.beans.PropertyChangeEvent;
import java.util.Map;

import org.glassfish.hk2.utilities.reflection.BeanReflectionHelper;
import org.glassfish.hk2.utilities.reflection.ClassReflectionHelper;
import org.glassfish.hk2.utilities.reflection.internal.ClassReflectionHelperImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanReflectionHelperTest {
    private final ClassReflectionHelper helper = new ClassReflectionHelperImpl();

    @Test
    public void reportsChangedBeanPropertiesFromPublicGetters() {
        final AccountState previousState = new AccountState("available", true, 3);
        final AccountState currentState = new AccountState("busy", true, 5);

        final PropertyChangeEvent[] events = BeanReflectionHelper.getChangeEvents(
                helper,
                previousState,
                currentState
        );

        assertThat(events)
                .extracting(PropertyChangeEvent::getPropertyName)
                .containsExactlyInAnyOrder("status", "priority");
        assertThat(events)
                .filteredOn(event -> "status".equals(event.getPropertyName()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getOldValue()).isEqualTo("available");
                    assertThat(event.getNewValue()).isEqualTo("busy");
                    assertThat(event.getSource()).isSameAs(currentState);
                });
        assertThat(events)
                .filteredOn(event -> "priority".equals(event.getPropertyName()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getOldValue()).isEqualTo(3);
                    assertThat(event.getNewValue()).isEqualTo(5);
                });
    }

    @Test
    public void convertsJavaBeanGettersToBeanLikeMap() {
        final AccountState state = new AccountState("available", false, 7);

        final Map<String, Object> values = BeanReflectionHelper.convertJavaBeanToBeanLikeMap(
                helper,
                state
        );

        assertThat(values)
                .containsEntry("status", "available")
                .containsEntry("active", false)
                .containsEntry("priority", 7)
                .doesNotContainKey("class");
    }

    public static final class AccountState {
        private final String status;
        private final boolean active;
        private final int priority;

        public AccountState(String status, boolean active, int priority) {
            this.status = status;
            this.active = active;
            this.priority = priority;
        }

        public String getStatus() {
            return status;
        }

        public boolean isActive() {
            return active;
        }

        public int getPriority() {
            return priority;
        }

        public String status() {
            return status;
        }
    }
}
