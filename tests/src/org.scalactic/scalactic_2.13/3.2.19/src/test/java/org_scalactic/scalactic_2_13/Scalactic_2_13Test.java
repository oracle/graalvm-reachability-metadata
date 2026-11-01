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

public class Scalactic_2_13Test {
    @Test
    void formatsBooleanSnapshotValues() {
        Snapshot snapshot = new Snapshot("inStock", true);

        assertEquals("inStock was true", snapshot.toString());
    }
}
