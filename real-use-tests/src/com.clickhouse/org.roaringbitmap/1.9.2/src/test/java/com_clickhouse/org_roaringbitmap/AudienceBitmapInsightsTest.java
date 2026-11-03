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
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.insights.BitmapAnalyser;
import org.roaringbitmap.insights.BitmapStatistics;
import org.roaringbitmap.insights.NaiveWriterRecommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the bitmap insights subsystem to choose an ingestion strategy from audience shard statistics.
public class AudienceBitmapInsightsTest {
    @Test
    @Timeout(60)
    public void recommendsWriterForMixedAudienceShardShapes() {
        RoaringBitmap sparseAudience = RoaringBitmap.bitmapOf(3, 8, 13, 1_000, 2_000);

        RoaringBitmap denseAudience = new RoaringBitmap();
        for (int value = 0; value < 10_000; value += 2) {
            denseAudience.add(65_536 + value);
        }

        RoaringBitmap campaignAudience = new RoaringBitmap();
        campaignAudience.add(131_072, 131_172);
        campaignAudience.runOptimize();

        BitmapStatistics statistics = BitmapAnalyser.analyse(
                List.of(sparseAudience, denseAudience, campaignAudience));

        assertEquals(3L, statistics.getBitmapsCount());
        assertEquals(3L, statistics.containerCount());
        assertEquals(1L, statistics.getArrayContainersStats().getContainersCount());
        assertEquals(5L, statistics.getArrayContainersStats().getCardinalitySum());
        assertEquals(1L, statistics.getBitmapContainerCount());
        assertEquals(1L, statistics.getRunContainerCount());
        assertEquals(5L, statistics.getArrayContainersStats().averageCardinality());

        String recommendation = NaiveWriterRecommender.recommend(statistics);
        assertTrue(recommendation.contains(".initialCapacity(1)"));
        assertTrue(recommendation.contains(".constantMemory()"));
    }
}
