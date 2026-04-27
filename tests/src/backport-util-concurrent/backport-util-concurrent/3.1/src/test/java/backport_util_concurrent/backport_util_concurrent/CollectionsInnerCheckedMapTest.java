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

public class CollectionsInnerCheckedMapTest {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void putAllInitializesTypedKeyAndValueArraysBeforeCopyingCompatibleEntries() {
        Map backingMap = new LinkedHashMap();
        Map checkedMap = Collections.checkedMap(backingMap, String.class, String.class);
        Map compatibleEntries = new LinkedHashMap();
        compatibleEntries.put("alpha", "alpha");
        compatibleEntries.put("beta", "beta");

        checkedMap.putAll(compatibleEntries);

        assertThat(checkedMap)
                .containsEntry("alpha", "alpha")
                .containsEntry("beta", "beta")
                .hasSize(2);
    }
}
