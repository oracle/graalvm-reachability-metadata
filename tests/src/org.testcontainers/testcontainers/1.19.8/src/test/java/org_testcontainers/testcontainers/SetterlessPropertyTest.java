/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SetterlessPropertyTest {
    @Test
    void updatesACollectionExposedOnlyByAGetter() throws Exception {
        SetterlessBean bean = new ObjectMapper().readValue("{\"values\":[\"one\",\"two\"]}", SetterlessBean.class);

        assertThat(bean.getValues()).containsExactly("one", "two");
    }

    public static class SetterlessBean {
        private final List<String> storage = new ArrayList<>();

        public List<String> getValues() {
            return storage;
        }
    }
}
