/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises JNA's structures returned by value to plan full and partial shipping cartons.
 */
public class NativeCartonPlanningTest {

    @Test
    @Timeout(60)
    public void plansCartonsWithoutLosingOrDuplicatingOrderedUnits() {
        CartonMath math = Native.load(Platform.C_LIBRARY_NAME, CartonMath.class);
        int unitsPerCarton = 24;
        List<Integer> orderQuantities = List.of(125, 72, 17, 0);
        List<Integer> fullCartons = new ArrayList<>();
        List<Integer> partialCartonUnits = new ArrayList<>();
        int cartonsToDispatch = 0;

        for (int quantity : orderQuantities) {
            // C div returns div_t by value, with the quotient followed by the remainder.
            DivisionResult allocation = math.div(quantity, unitsPerCarton);
            fullCartons.add(allocation.quot);
            partialCartonUnits.add(allocation.rem);
            cartonsToDispatch += allocation.quot + (allocation.rem == 0 ? 0 : 1);

            assertEquals(quantity, allocation.quot * unitsPerCarton + allocation.rem,
                    "The packing plan must account for every ordered unit exactly once");
            assertTrue(allocation.rem >= 0 && allocation.rem < unitsPerCarton,
                    "A partial carton must contain fewer units than a full carton");
        }

        assertEquals(List.of(5, 3, 0, 0), fullCartons);
        assertEquals(List.of(5, 0, 17, 0), partialCartonUnits);
        assertEquals(10, cartonsToDispatch,
                "Only nonempty partial cartons contribute to the dispatch count");
    }

    public interface CartonMath extends Library {
        DivisionResult div(int numerator, int denominator);
    }

    @FieldOrder({"quot", "rem"})
    public static class DivisionResult extends Structure implements Structure.ByValue {
        public int quot;
        public int rem;
    }
}
