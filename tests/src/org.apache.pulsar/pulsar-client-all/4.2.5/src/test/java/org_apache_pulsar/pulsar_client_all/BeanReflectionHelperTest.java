/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyChangeEvent;
import java.util.Map;

import org.apache.pulsar.shade.org.glassfish.hk2.utilities.reflection.BeanReflectionHelper;
import org.apache.pulsar.shade.org.glassfish.hk2.utilities.reflection.internal.ClassReflectionHelperImpl;
import org.junit.jupiter.api.Test;

public class BeanReflectionHelperTest {
    @Test
    void convertsJavaBeanGettersToPropertyMap() {
        ClassReflectionHelperImpl helper = new ClassReflectionHelperImpl();
        try {
            Map<String, Object> properties = BeanReflectionHelper.convertJavaBeanToBeanLikeMap(
                    helper, new ClientSettings("persistent", 3));

            assertThat(properties).containsEntry("mode", "persistent").containsEntry("retries", 3);
        } finally {
            helper.clean(new ClientSettings("unused", 0).getClass());
        }
    }

    @Test
    void comparesBeanGettersAcrossInstances() {
        ClassReflectionHelperImpl helper = new ClassReflectionHelperImpl();
        try {
            PropertyChangeEvent[] changes = BeanReflectionHelper.getChangeEvents(
                    helper,
                    new ClientSettings("persistent", 3),
                    new ClientSettings("non-persistent", 4));

            assertThat(changes)
                    .extracting(PropertyChangeEvent::getPropertyName)
                    .contains("mode", "retries");
        } finally {
            helper.dispose();
        }
    }

    public static class ClientSettings {
        private final String mode;
        private final int retries;

        public ClientSettings(String mode, int retries) {
            this.mode = mode;
            this.retries = retries;
        }

        public String getMode() {
            return mode;
        }

        public int getRetries() {
            return retries;
        }
    }
}
