/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalactic.scalactic_2_13;

import org.junit.jupiter.api.Test;
import org.scalactic.Snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourcesTest {
    @Test
    void formatsNamedNumericSnapshots() {
        Snapshot snapshot = new Snapshot("remainingItems", 17);

        assertEquals("remainingItems was 17", snapshot.toString());
    }

    @Test
    void prettifiesStringAndNullSnapshotValues() {
        Snapshot description = new Snapshot("description", "blue notebook");
        Snapshot missingValue = new Snapshot("optionalNote", null);

        assertEquals("description was \"blue notebook\"", description.toString());
        assertEquals("optionalNote was null", missingValue.toString());
    }
}
