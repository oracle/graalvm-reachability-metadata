/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.pulsar.shade.org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;

public class ReflectionToStringBuilderTest {
    @Test
    void rendersPrivateFieldsAcrossAClassHierarchy() {
        String rendered = ReflectionToStringBuilder.toString(
                new ChildValue("client", 4), ToStringStyle.SHORT_PREFIX_STYLE);

        assertThat(rendered).contains("name=client").contains("count=4");
    }

    public static class ParentValue {
        private final String name;

        public ParentValue(String name) {
            this.name = name;
        }
    }

    public static class ChildValue extends ParentValue {
        private final int count;

        public ChildValue(String name, int count) {
            super(name);
            this.count = count;
        }
    }
}
