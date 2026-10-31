/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_typelevel.simulacrum_scalafix_annotations_3;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import scala.collection.immutable.List;
import scala.jdk.javaapi.CollectionConverters;
import simulacrum.noop;
import simulacrum.op;
import simulacrum.typeclass;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Simulacrum_scalafix_annotations_3Test {
    @Test
    void typeclassDefaultsExcludeNoParentsAndGenerateAllOperations() {
        List<String> excludedParents = typeclass.$lessinit$greater$default$1();
        boolean generateAllOps = typeclass.$lessinit$greater$default$2();

        assertTrue(excludedParents.isEmpty());
        assertTrue(generateAllOps);
        assertDoesNotThrow(() -> new typeclass(excludedParents, generateAllOps));
    }

    @Test
    void operationDefaultsToRenamingWithoutAnAlias() {
        boolean alias = op.$lessinit$greater$default$2();

        assertFalse(alias);
        assertDoesNotThrow(() -> new op("|+|", alias));
    }

    @Test
    void constructsExplicitTypeclassOperationAndOptOutMarkers() {
        List<String> excludedParents = CollectionConverters.asScala(
                Arrays.asList("Serializable", "Product")).toList();

        // These compile-time markers accept configuration but retain no runtime attributes.
        assertDoesNotThrow(() -> new typeclass(excludedParents, false));
        assertDoesNotThrow(() -> new op("combine", true));
        assertDoesNotThrow(noop::new);
    }
}
