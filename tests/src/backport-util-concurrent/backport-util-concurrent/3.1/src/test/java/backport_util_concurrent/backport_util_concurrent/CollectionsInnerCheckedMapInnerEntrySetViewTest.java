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

public class CollectionsInnerCheckedMapInnerEntrySetViewTest {
    @Test
    void entrySetCopiesEntriesIntoNonEmptyTypedArray() {
        Map checkedMap = Collections.checkedMap(new LinkedHashMap(), String.class, Integer.class);
        checkedMap.put("alpha", Integer.valueOf(1));
        checkedMap.put("bravo", Integer.valueOf(2));
        Map.Entry[] destination = new Map.Entry[2];

        Object[] returned = checkedMap.entrySet().toArray(destination);

        assertThat(returned).isSameAs(destination);
        assertThat(destination[0].getKey()).isEqualTo("alpha");
        assertThat(destination[0].getValue()).isEqualTo(Integer.valueOf(1));
        assertThat(destination[1].getKey()).isEqualTo("bravo");
        assertThat(destination[1].getValue()).isEqualTo(Integer.valueOf(2));
    }
}
