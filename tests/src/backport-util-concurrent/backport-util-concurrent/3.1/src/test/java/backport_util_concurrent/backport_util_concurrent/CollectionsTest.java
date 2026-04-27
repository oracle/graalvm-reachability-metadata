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

public class CollectionsTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void checkedMapEntrySetToArrayReturnsCheckedEntryViews() {
        Map backingMap = new LinkedHashMap();
        backingMap.put("name", "value");
        Map checkedMap = Collections.checkedMap(backingMap, String.class, String.class);

        Object[] entries = checkedMap.entrySet().toArray();

        assertThat(entries).hasSize(1);
        assertThat(entries[0]).isInstanceOf(Map.Entry.class);

        Map.Entry entry = (Map.Entry) entries[0];
        assertThat(entry.getKey()).isEqualTo("name");
        assertThat(entry.getValue()).isEqualTo("value");
        assertThat(entry.setValue("updated")).isEqualTo("value");
        assertThat(backingMap).containsEntry("name", "updated");
        assertThatThrownBy(() -> entry.setValue(1))
                .isInstanceOf(ClassCastException.class);
    }
}
