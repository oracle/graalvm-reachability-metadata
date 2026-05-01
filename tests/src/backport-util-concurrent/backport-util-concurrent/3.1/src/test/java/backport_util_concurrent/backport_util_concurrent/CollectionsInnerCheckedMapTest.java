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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionsInnerCheckedMapTest {
    @Test
    void putAllCopiesCompatibleEntriesIntoCheckedMap() {
        Map checkedMap = Collections.checkedMap(new LinkedHashMap(), String.class, String.class);
        Map entries = new LinkedHashMap();
        entries.put("alpha", "alpha");
        entries.put("bravo", "bravo");

        checkedMap.putAll(entries);

        assertThat(checkedMap).containsEntry("alpha", "alpha");
        assertThat(checkedMap).containsEntry("bravo", "bravo");
        assertThat(checkedMap).hasSize(2);
    }
}
