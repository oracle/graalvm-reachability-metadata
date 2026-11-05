/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.FastAggregation;
import org.roaringbitmap.RoaringBitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the bitmap aggregation subsystem to combine shard-local campaign audiences.
public class SegmentAudienceAggregationTest {
    @Test
    @Timeout(60)
    public void combinesShardAudiencesForCampaignEligibility() {
        RoaringBitmap mobileAudience = RoaringBitmap.bitmapOf(
                101, 103, 65_536, 65_537, 100_000);
        RoaringBitmap webAudience = RoaringBitmap.bitmapOf(
                102, 103, 65_537, 100_001, 200_000);
        RoaringBitmap partnerAudience = RoaringBitmap.bitmapOf(
                101, 104, 65_538, 100_000, 200_000);

        List<RoaringBitmap> shardAudiences = List.of(
                mobileAudience, webAudience, partnerAudience);
        RoaringBitmap campaignAudience = FastAggregation.or(shardAudiences.iterator());
        RoaringBitmap optedOut = RoaringBitmap.bitmapOf(103, 65_537, 200_000);
        campaignAudience.andNot(optedOut);

        int eligibleSourceMatches = FastAggregation.orCardinality(
                mobileAudience, webAudience, partnerAudience);

        assertArrayEquals(
                new int[] {101, 102, 104, 65_536, 65_538, 100_000, 100_001},
                campaignAudience.toArray());
        assertEquals(7L, campaignAudience.getLongCardinality());
        assertEquals(10, eligibleSourceMatches);
        assertTrue(campaignAudience.contains(65_538));
        assertFalse(campaignAudience.contains(103));
        assertEquals(65_536, campaignAudience.select(3));
        assertEquals(5L, campaignAudience.rankLong(65_538));
        assertEquals(3, shardAudiences.size());
    }
}
