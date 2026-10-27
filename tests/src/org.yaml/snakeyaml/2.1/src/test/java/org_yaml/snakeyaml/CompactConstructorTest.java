/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.extensions.compactnotation.CompactConstructor;

public class CompactConstructorTest {

    @Test
    void constructsCompactBeanUsingPrivateDeclaredStringConstructor() {
        String yaml = "%s(exampleName)".formatted(PrivateCompactBean.class.getName());

        PrivateCompactBean bean = new Yaml(new CompactConstructor()).load(yaml);

        assertThat(bean.getName()).isEqualTo("exampleName");
    }

    @Test
    void constructsCompactBeanAndAppliesNamedProperties() {
        String yaml = "%s(exampleName, role=maintainer)"
                .formatted(PrivateCompactBean.class.getName());

        PrivateCompactBean bean = new Yaml(new CompactConstructor()).load(yaml);

        assertThat(bean.getName()).isEqualTo("exampleName");
        assertThat(bean.getRole()).isEqualTo("maintainer");
    }

    public static final class PrivateCompactBean {
        private final String name;
        private String role;

        private PrivateCompactBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
