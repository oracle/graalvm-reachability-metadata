/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.TypeDescription;
import org.testcontainers.shaded.org.yaml.snakeyaml.constructor.Constructor;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertySubstituteTest {
    @Test
    void usesConfiguredAccessorsForASubstitutedProperty() {
        TypeDescription description = new TypeDescription(Bean.class);
        description.substituteProperty("message", String.class, "readMessage", "assignMessage");
        Yaml yaml = new Yaml(new Constructor(description));

        Bean bean = yaml.load("message: substituted");

        assertThat(bean.readMessage()).isEqualTo("substituted");
        assertThat(description.getProperty("message").get(bean)).isEqualTo("substituted");
    }

    public static class Bean {
        private String message;

        public String readMessage() {
            return message;
        }

        public void assignMessage(String message) {
            this.message = message;
        }
    }
}
