/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.pulsar.shade.org.yaml.snakeyaml.LoaderOptions;
import org.apache.pulsar.shade.org.yaml.snakeyaml.TypeDescription;
import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.apache.pulsar.shade.org.yaml.snakeyaml.constructor.Constructor;
import org.junit.jupiter.api.Test;

public class PropertySubstituteTest {

    @Test
    void populatesAndReadsACollectionThroughSubstitutedAccessors() {
        final TypeDescription description = new TypeDescription(Labels.class);
        description.substituteProperty("labels", List.class, "getLabels", "addLabel", String.class);
        final Constructor constructor = new Constructor(description, new LoaderOptions());

        final Labels labels = new Yaml(constructor).loadAs("labels: [primary, replicated]", Labels.class);

        assertThat(labels.getLabels()).containsExactly("primary", "replicated");
        assertThat(description.getProperty("labels").get(labels)).isEqualTo(labels.getLabels());
    }

    public static final class Labels {
        private final List<String> labels = new ArrayList<>();

        public void addLabel(String label) {
            labels.add(label);
        }

        public List<String> getLabels() {
            return labels;
        }
    }
}
