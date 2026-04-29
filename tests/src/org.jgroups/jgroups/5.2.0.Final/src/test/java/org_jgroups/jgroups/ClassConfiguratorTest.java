/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.conf.ClassConfigurator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassConfiguratorTest {
    private static final short CUSTOM_MAGIC_ID = 10_240;

    @Test
    void registersAndCreatesCustomClassByMagicNumber() throws Exception {
        ClassConfigurator.add(CUSTOM_MAGIC_ID, ConfiguredPojo.class);

        ConfiguredPojo created = ClassConfigurator.create(CUSTOM_MAGIC_ID);

        assertThat(ClassConfigurator.getMagicNumber(ConfiguredPojo.class)).isEqualTo(CUSTOM_MAGIC_ID);
        assertThat(created.marker()).isEqualTo("constructed");
    }

    public static class ConfiguredPojo {
        private final String marker;

        public ConfiguredPojo() {
            marker = "constructed";
        }

        public String marker() {
            return marker;
        }
    }
}
