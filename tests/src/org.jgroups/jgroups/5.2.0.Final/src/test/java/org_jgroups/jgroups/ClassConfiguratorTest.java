/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.conf.ClassConfigurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassConfiguratorTest {
    private static final short CUSTOM_MAGIC_ID = 32_001;

    static {
        configureJGroupsLoopbackDefaults();
    }

    @BeforeAll
    static void configureLoopbackDefaults() {
        configureJGroupsLoopbackDefaults();
    }

    @Test
    void registersCustomMagicClassAndCreatesInstancesByMagicId() throws Exception {
        ClassConfigurator.add(CUSTOM_MAGIC_ID, CustomPayload.class);

        CustomPayload first = ClassConfigurator.create(CUSTOM_MAGIC_ID);
        CustomPayload second = ClassConfigurator.create(CUSTOM_MAGIC_ID);

        assertThat(ClassConfigurator.getMagicNumber(CustomPayload.class)).isEqualTo(CUSTOM_MAGIC_ID);
        assertThat(first).isInstanceOf(CustomPayload.class);
        assertThat(second).isInstanceOf(CustomPayload.class);
        assertThat(second).isNotSameAs(first);
    }

    private static void configureJGroupsLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    public static class CustomPayload {
        public CustomPayload() {
        }
    }
}
