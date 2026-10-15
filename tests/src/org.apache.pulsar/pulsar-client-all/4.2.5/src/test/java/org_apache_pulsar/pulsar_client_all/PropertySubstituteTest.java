/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.apache.pulsar.shade.org.yaml.snakeyaml.introspector.PropertySubstitute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class PropertySubstituteTest {
    @Test
    void readsWritesAndFillsBeanProperties() throws Exception {
        Config config = new Config();
        PropertySubstitute name =
                property("name", String.class, "getName", "setName");
        PropertySubstitute tags =
                property("tags", List.class, "getTags", "addTag", String.class);
        PropertySubstitute scores = property(
                "scores", Map.class, "getScores", "putScore", String.class, Integer.class);
        PropertySubstitute codes =
                property("codes", List.class, "getCodes", "addCode", String.class);
        PropertySubstitute direct = new PropertySubstitute("direct", String.class);
        direct.setTargetType(Config.class);

        name.set(config, "broker");
        tags.set(config, List.of("fast", "durable"));
        scores.set(config, Map.of("quality", 9));
        codes.set(config, new String[] {"A", "B"});
        direct.set(config, "field");

        assertThat(name.get(config)).isEqualTo("broker");
        assertThat(tags.get(config)).isEqualTo(List.of("fast", "durable"));
        assertThat(scores.get(config)).isEqualTo(Map.of("quality", 9));
        assertThat(codes.get(config)).isEqualTo(List.of("A", "B"));
        assertThat(direct.get(config)).isEqualTo("field");
    }

    private static PropertySubstitute property(
            String name,
            Class<?> type,
            String readMethod,
            String writeMethod,
            Class<?>... parameters) {
        PropertySubstitute property =
                new PropertySubstitute(name, type, readMethod, writeMethod, parameters);
        property.setTargetType(Config.class);
        return property;
    }

    public static final class Config {
        private String name;
        private final java.util.ArrayList<String> tags = new java.util.ArrayList<>();
        private final java.util.HashMap<String, Integer> scores = new java.util.HashMap<>();
        private final java.util.ArrayList<String> codes = new java.util.ArrayList<>();
        private String direct;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTags() {
            return tags;
        }

        public void addTag(String tag) {
            tags.add(tag);
        }

        public Map<String, Integer> getScores() {
            return scores;
        }

        public void putScore(String key, Integer value) {
            scores.put(key, value);
        }

        public List<String> getCodes() {
            return codes;
        }

        public void addCode(String code) {
            codes.add(code);
        }
    }
}
