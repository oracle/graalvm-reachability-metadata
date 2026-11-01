/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scalactic.scalactic_2_13;

import org.junit.jupiter.api.Test;
import org.scalactic.source.ObjectMeta;
import org.scalactic.source.ObjectMeta$;
import scala.Tuple2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ObjectMetaAnonymous1AnonymousFunction1Test {
    @Test
    void rejectsAnUnknownAttributeAfterCheckingSpecializedAccessors() {
        ObjectMeta metadata = ObjectMeta$.MODULE$.apply(new Tuple2<>("notebook", 3));

        assertFalse(metadata.hasField("price"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> metadata.value("price"));

        assertEquals("'price' is not attribute for this instance.", exception.getMessage());
        assertEquals("notebook", metadata.value("_1"));
    }
}
