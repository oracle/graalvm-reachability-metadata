/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import java.io.ByteArrayInputStream;

import org.apache.logging.log4j.util.FilteredObjectInputStream;
import org.apache.logging.log4j.util.SortedArrayStringMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SortedArrayStringMapTest {

    @Test
    void preservesSortedEntriesAndFrozenStateThroughSerialization() throws Exception {
        SortedArrayStringMap original = new SortedArrayStringMap();
        original.putValue("tenant", "acme");
        original.putValue("attempt", Integer.valueOf(3));
        original.putValue("optional", null);
        original.freeze();

        SortedArrayStringMap copy = SerializationSupport.roundTrip(original, SortedArrayStringMap.class);

        assertThat(copy).isEqualTo(original);
        assertThat(copy.size()).isEqualTo(3);
        assertThat(copy.getKeyAt(0)).isEqualTo("attempt");
        assertThat(copy.<Integer>getValue("attempt")).isEqualTo(3);
        assertThat(copy.<String>getValue("tenant")).isEqualTo("acme");
        assertThat(copy.containsKey("optional")).isTrue();
        assertThat(copy.<Object>getValue("optional")).isNull();
        assertThat(copy.isFrozen()).isTrue();
        assertThatThrownBy(() -> copy.putValue("tenant", "other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void readsContextValuesThroughFilteredStream() throws Exception {
        SortedArrayStringMap original = new SortedArrayStringMap();
        original.putValue("request", "r-19");
        original.putValue("attempt", Integer.valueOf(2));
        byte[] bytes = SerializationSupport.serialize(original);

        try (FilteredObjectInputStream input = new FilteredObjectInputStream(new ByteArrayInputStream(bytes))) {
            SortedArrayStringMap copy = (SortedArrayStringMap) input.readObject();

            assertThat(copy).isEqualTo(original);
            assertThat(copy.<String>getValue("request")).isEqualTo("r-19");
            copy.putValue("attempt", Integer.valueOf(3));
            assertThat(copy.<Integer>getValue("attempt")).isEqualTo(3);
        }
    }
}
