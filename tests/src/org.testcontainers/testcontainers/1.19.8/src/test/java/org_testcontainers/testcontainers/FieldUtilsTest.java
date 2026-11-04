/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.reflect.FieldUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldUtilsTest {
    @Test
    void readsAndWritesInheritedAndPrivateState() throws Exception {
        Settings settings = new Settings();
        assertThat(FieldUtils.readField(settings, "label")).isEqualTo("initial");
        FieldUtils.writeField(settings, "label", "updated");
        assertThat(settings.label).isEqualTo("updated");
        assertThat(FieldUtils.readDeclaredField(settings, "enabled", true)).isEqualTo(false);
        FieldUtils.writeDeclaredField(settings, "enabled", true, true);
        assertThat(settings.isEnabled()).isTrue();
        assertThat(FieldUtils.getAllFieldsList(Settings.class)).extracting(field -> field.getName())
                .contains("enabled", "label");
        assertThat(FieldUtils.readStaticField(Settings.class, "CATEGORY")).isEqualTo("containers");
    }

    public interface Categorized {
        String CATEGORY = "containers";
    }

    public static class BaseSettings {
        public String label = "initial";
    }

    public static class Settings extends BaseSettings implements Categorized {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }
    }
}
