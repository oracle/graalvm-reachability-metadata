/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.conversions.EnumConversion;
import com.univocity.parsers.conversions.EnumSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnumConversionTest {
    @Test
    public void convertsUsingCustomFieldValues() {
        final EnumConversion<FieldBackedStatus> conversion = new EnumConversion<>(
                FieldBackedStatus.class,
                null,
                null,
                "wireValue",
                EnumSelector.CUSTOM_FIELD
        );

        assertEquals(FieldBackedStatus.APPROVED, conversion.execute("approved"));
        assertEquals("rejected", conversion.revert(FieldBackedStatus.REJECTED));
    }

    @Test
    public void convertsUsingCustomInstanceMethodValues() {
        final EnumConversion<MethodBackedStatus> conversion = new EnumConversion<>(
                MethodBackedStatus.class,
                null,
                null,
                "externalName",
                EnumSelector.CUSTOM_METHOD
        );

        assertEquals(MethodBackedStatus.PAID, conversion.execute("paid-in-full"));
        assertEquals("pending-payment", conversion.revert(MethodBackedStatus.PENDING));
    }

    @Test
    public void convertsUsingCustomStaticLookupMethod() {
        final EnumConversion<LookupBackedStatus> conversion = new EnumConversion<>(
                LookupBackedStatus.class,
                null,
                null,
                "fromExternalName",
                EnumSelector.CUSTOM_METHOD
        );

        assertEquals(LookupBackedStatus.ACTIVE, conversion.execute("active-account"));
        assertEquals(LookupBackedStatus.SUSPENDED, conversion.execute("suspended-account"));
    }

    public enum FieldBackedStatus {
        APPROVED("approved"),
        REJECTED("rejected");

        private final String wireValue;

        FieldBackedStatus(String wireValue) {
            this.wireValue = wireValue;
        }
    }

    public enum MethodBackedStatus {
        PAID("paid-in-full"),
        PENDING("pending-payment");

        private final String label;

        MethodBackedStatus(String label) {
            this.label = label;
        }

        public String externalName() {
            return label;
        }
    }

    public enum LookupBackedStatus {
        ACTIVE,
        SUSPENDED;

        public static LookupBackedStatus fromExternalName(String externalName) {
            if ("active-account".equals(externalName)) {
                return ACTIVE;
            }
            if ("suspended-account".equals(externalName)) {
                return SUSPENDED;
            }
            throw new IllegalArgumentException("Unknown status: " + externalName);
        }
    }
}
