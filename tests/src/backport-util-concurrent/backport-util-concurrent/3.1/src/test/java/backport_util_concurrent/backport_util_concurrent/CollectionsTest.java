/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package backport_util_concurrent.backport_util_concurrent;

import edu.emory.mathcs.backport.java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionsTest {
    @Test
    void checkedMapEntrySetToArrayReturnsCheckedEntryViews() {
        Map backingMap = new LinkedHashMap();
        backingMap.put("alpha", "one");
        backingMap.put("bravo", "two");
        Map checkedMap = Collections.checkedMap(backingMap, String.class, String.class);

        Set checkedEntries = checkedMap.entrySet();
        Object[] entries = checkedEntries.toArray();

        assertThat(entries).hasSize(2);
        assertThat(entries).allSatisfy(entry -> assertThat(entry).isInstanceOf(Map.Entry.class));
        assertThat(entries).extracting(entry -> ((Map.Entry) entry).getKey()).containsExactly("alpha", "bravo");
        assertThat(entries).extracting(entry -> ((Map.Entry) entry).getValue()).containsExactly("one", "two");
    }
}
