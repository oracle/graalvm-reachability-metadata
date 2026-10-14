/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.primitives.UnsignedBytes;
import org.junit.jupiter.api.Test;

public class UnsignedBytesInnerLexicographicalComparatorHolderInnerUnsafeComparatorAnonymous1Test {
    @Test
    void comparesLongByteSequencesWithTheBestAvailableComparator() {
        byte[] lower = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8};
        byte[] higher = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, (byte) 200};

        int comparison = UnsignedBytes.lexicographicalComparator().compare(lower, higher);

        assertThat(comparison).isNegative();
    }
}
