/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class JacksonAnnotationIntrospectorTest {
    @Test
    void discoversEnumNamesAndAliases() {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Status[] values = Status.values();
        String[] names = {null, null};
        String[][] aliases = new String[values.length][];

        introspector.findEnumValues(Status.class, values, names);
        introspector.findEnumAliases(Status.class, values, aliases);

        assertThat(introspector.findEnumValue(Status.IN_PROGRESS)).isEqualTo("in-progress");
        assertThat(names).containsExactly("in-progress", null);
        assertThat(aliases[Status.DONE.ordinal()]).containsExactly("complete", "finished");
    }

    private enum Status {
        @JsonProperty("in-progress")
        IN_PROGRESS,
        @JsonAlias({"complete", "finished"})
        DONE
    }
}
