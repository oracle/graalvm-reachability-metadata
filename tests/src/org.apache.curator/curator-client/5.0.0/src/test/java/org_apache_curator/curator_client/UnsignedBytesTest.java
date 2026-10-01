/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_curator.curator_client;

import org.apache.curator.shaded.com.google.common.primitives.UnsignedBytes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsignedBytesTest {
    @Test
    void comparesByteArraysUsingThePublicUnsignedComparator() {
        assertThat(UnsignedBytes.lexicographicalComparator().compare(
                new byte[] {(byte) 0xff}, new byte[] {0})).isPositive();
        assertThat(UnsignedBytes.toInt((byte) 0xff)).isEqualTo(255);
    }
}
