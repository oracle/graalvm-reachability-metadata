/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_modulith.spring_modulith_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.runtime.minidev.asm.Accessor;
import org.springframework.modulith.runtime.minidev.asm.BeansAccess;

public class BeansAccessTest {
    @Test
    void loadsExistingAccessorAndReadsAndWritesBeanProperties() {
        BeansAccess<ModuleSettings> access = BeansAccess.get(ModuleSettings.class);
        ModuleSettings settings = access.newInstance();

        assertThat(access.getAccessors()).extracting(Accessor::getName).containsExactly("name");
        assertThat(access.getMap()).containsOnlyKeys("name");
        assertThat(access.getIndex("name")).isZero();
        assertThat(access.getIndex("unknown")).isEqualTo(-1);
        assertThat(access.get(settings, "name")).isEqualTo("unassigned");

        access.set(settings, "name", "inventory");

        assertThat(settings.name).isEqualTo("inventory");
        assertThat(access.get(settings, access.getIndex("name"))).isEqualTo("inventory");

        access.set(settings, access.getIndex("name"), "billing");

        assertThat(access.get(settings, "name")).isEqualTo("billing");
        assertThat(BeansAccess.get(ModuleSettings.class)).isSameAs(access);
    }

    public static class ModuleSettings {
        public String name = "unassigned";
    }

    // The AccAccess suffix lets BeansAccess load a compiled accessor without generating bytecode.
    public static class ModuleSettingsAccAccess extends BeansAccess<ModuleSettings> {
        @Override
        public void set(ModuleSettings settings, int index, Object value) {
            if (index != 0) {
                throw new IllegalArgumentException("Unknown property index: " + index);
            }
            settings.name = (String) value;
        }

        @Override
        public Object get(ModuleSettings settings, int index) {
            if (index != 0) {
                throw new IllegalArgumentException("Unknown property index: " + index);
            }
            return settings.name;
        }

        @Override
        public ModuleSettings newInstance() {
            return new ModuleSettings();
        }
    }
}
