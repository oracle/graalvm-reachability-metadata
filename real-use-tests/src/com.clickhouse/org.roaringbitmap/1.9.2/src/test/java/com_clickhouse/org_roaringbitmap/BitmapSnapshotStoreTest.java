/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.RoaringBitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the bitmap serialization subsystem to persist an audience membership index and restore it for queries.
public class BitmapSnapshotStoreTest {
    @Test
    @Timeout(60)
    public void restoresAudienceSnapshotForMembershipQueries() throws IOException {
        RoaringBitmap audience = RoaringBitmap.bitmapOf(
                101, 102, 103, 65_536, 65_537, 1_000_000);
        audience.runOptimize();

        ByteArrayOutputStream snapshotBytes = new ByteArrayOutputStream(
                audience.serializedSizeInBytes());
        try (DataOutputStream output = new DataOutputStream(snapshotBytes)) {
            audience.serialize(output);
        }

        RoaringBitmap restoredAudience = new RoaringBitmap();
        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(snapshotBytes.toByteArray()))) {
            restoredAudience.deserialize(input);
        }

        assertArrayEquals(audience.toArray(), restoredAudience.toArray());
        assertEquals(audience.getLongCardinality(), restoredAudience.getLongCardinality());
        assertTrue(restoredAudience.contains(65_537));
        assertFalse(restoredAudience.contains(104));
        assertEquals(65_536, restoredAudience.select(3));
        assertEquals(5L, restoredAudience.rankLong(65_537));
    }
}
