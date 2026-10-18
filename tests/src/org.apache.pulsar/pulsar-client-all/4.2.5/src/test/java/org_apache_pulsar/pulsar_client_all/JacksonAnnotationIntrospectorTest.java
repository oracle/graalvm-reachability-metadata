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
    void discoversEnumNamesAndAliasesFromAnnotations() {
        final JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        final State[] values = State.values();
        final String[] names = {"READY", "DONE"};
        final String[][] aliases = new String[values.length][];

        introspector.findEnumValues(State.class, values, names);
        introspector.findEnumAliases(State.class, values, aliases);

        assertThat(introspector.findEnumValue(State.READY)).isEqualTo("ready-state");
        assertThat(names).containsExactly("ready-state", "DONE");
        assertThat(aliases[1]).containsExactly("complete", "finished");
    }

    public enum State {
        @JsonProperty("ready-state")
        READY,

        @JsonAlias({"complete", "finished"})
        DONE
    }
}
