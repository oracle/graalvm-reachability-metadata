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
    private static final short CUSTOM_MAGIC_ID = 1600;

    @Test
    void registersAndCreatesCustomClassByMagicId() throws Exception {
        ClassConfigurator.add(CUSTOM_MAGIC_ID, CustomMagicType.class);

        CustomMagicType created = ClassConfigurator.create(CUSTOM_MAGIC_ID);

        assertThat(created).isInstanceOf(CustomMagicType.class);
        assertThat(created.createdByDefaultConstructor()).isTrue();
        assertThat(ClassConfigurator.getMagicNumber(CustomMagicType.class)).isEqualTo(CUSTOM_MAGIC_ID);
    }

    public static class CustomMagicType {
        private final boolean createdByDefaultConstructor;

        public CustomMagicType() {
            createdByDefaultConstructor = true;
        }

        boolean createdByDefaultConstructor() {
            return createdByDefaultConstructor;
        }
    }
}
