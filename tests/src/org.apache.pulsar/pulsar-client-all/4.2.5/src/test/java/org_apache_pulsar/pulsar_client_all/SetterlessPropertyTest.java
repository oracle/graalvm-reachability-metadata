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
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class SetterlessPropertyTest {

    @Test
    void populatesAMutableCollectionReturnedByAGetter() throws Exception {
        final SetterlessBean bean = new ObjectMapper()
                .readValue("{\"topics\":[\"orders\",\"events\"]}", SetterlessBean.class);

        assertThat(bean.getTopics()).containsExactly("orders", "events");
    }

    public static final class SetterlessBean {
        private final List<String> topics = new ArrayList<>();

        public List<String> getTopics() {
            return topics;
        }
    }
}
