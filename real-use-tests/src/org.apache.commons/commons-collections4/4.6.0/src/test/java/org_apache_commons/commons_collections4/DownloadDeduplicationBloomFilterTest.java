/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.bloomfilter.EnhancedDoubleHasher;
import org.apache.commons.collections4.bloomfilter.Hasher;
import org.apache.commons.collections4.bloomfilter.Shape;
import org.apache.commons.collections4.bloomfilter.SimpleBloomFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Bloom filter subsystem for deduplicating downloads in an asset service.
 */
public class DownloadDeduplicationBloomFilterTest {
    @Test
    @Timeout(60)
    public void downloadsEachAssetOnlyOnceAndRestoresTheMembershipIndex() {
        Shape shape = Shape.fromKM(3, 1024);
        SimpleBloomFilter processedAssets = new SimpleBloomFilter(shape);
        List<String> downloadedAssets = new ArrayList<>();
        List<String> downloadRequests = List.of(
                "asset:logo.svg",
                "asset:manual.pdf",
                "asset:logo.svg",
                "asset:checkout.js");

        for (String asset : downloadRequests) {
            Hasher hasher = hasherFor(asset);
            if (!processedAssets.contains(hasher)) {
                processedAssets.merge(hasher);
                downloadedAssets.add(asset);
            }
        }

        assertEquals(List.of("asset:logo.svg", "asset:manual.pdf", "asset:checkout.js"), downloadedAssets);
        assertTrue(processedAssets.contains(hasherFor("asset:logo.svg")));
        assertTrue(processedAssets.contains(hasherFor("asset:manual.pdf")));
        assertEquals(3, processedAssets.estimateN());

        SimpleBloomFilter restoredIndex = processedAssets.copy();
        assertTrue(restoredIndex.contains(hasherFor("asset:checkout.js")));
        assertEquals(processedAssets.cardinality(), restoredIndex.cardinality());
    }

    private static Hasher hasherFor(String asset) {
        return new EnhancedDoubleHasher(asset.getBytes(StandardCharsets.UTF_8));
    }
}
