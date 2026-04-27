/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CollectionsInnerCheckedMapInnerEntrySetViewTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void toArrayWithTypedDestinationReturnsCheckedEntryViews() {
        Map backingMap = new LinkedHashMap();
        backingMap.put("first", "value");
        Map checkedMap = Collections.checkedMap(backingMap, String.class, String.class);
        Map.Entry[] destination = new Map.Entry[checkedMap.entrySet().size()];

        Object[] entries = checkedMap.entrySet().toArray(destination);

        assertThat(entries).isSameAs(destination);
        assertThat(entries).hasSize(1);
        assertThat(entries[0]).isInstanceOf(Map.Entry.class);

        Map.Entry entry = (Map.Entry) entries[0];
        assertThat(entry.getKey()).isEqualTo("first");
        assertThat(entry.getValue()).isEqualTo("value");
        assertThat(entry.setValue("updated")).isEqualTo("value");
        assertThat(backingMap).containsEntry("first", "updated");
        assertThatThrownBy(() -> entry.setValue(Integer.valueOf(1)))
                .isInstanceOf(ClassCastException.class)
                .hasMessageContaining("values of type java.lang.String");
    }
}
