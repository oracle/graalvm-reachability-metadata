/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jdiff_tests;

import java.nio.file.Path;

import jdiff.API;
import jdiff.APIComparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class APIComparatorTest {
    @TempDir
    Path directory;

    @Test
    void findsNoChangesInEquivalentApiDescriptions() throws Exception {
        String contents = ApiFiles.packageWithClass("example.catalog", ApiFiles.method("lookup", "int"));
        API oldApi = ApiFiles.read(directory, "Baseline", contents);
        API newApi = ApiFiles.read(directory, "Current", contents);
        APIComparator comparator = new APIComparator();

        comparator.compareAPIs(oldApi, newApi);

        assertTrue(comparator.apiDiff.packagesAdded.isEmpty());
        assertTrue(comparator.apiDiff.packagesRemoved.isEmpty());
        assertTrue(comparator.apiDiff.packagesChanged.isEmpty());
        assertEquals(0.0, comparator.apiDiff.pdiff);
    }

    @Test
    void identifiesAddedAndRemovedPackages() throws Exception {
        String common = ApiFiles.packageWithClass("example.common", "");
        API oldApi = ApiFiles.read(directory, "Baseline",
                common + ApiFiles.packageWithClass("example.retired", ""));
        API newApi = ApiFiles.read(directory, "Current",
                common + ApiFiles.packageWithClass("example.introduced", ""));
        APIComparator comparator = new APIComparator();

        comparator.compareAPIs(oldApi, newApi);

        assertEquals(1, comparator.apiDiff.packagesAdded.size());
        assertEquals(1, comparator.apiDiff.packagesRemoved.size());
        assertTrue(comparator.apiDiff.packagesChanged.isEmpty());
        assertEquals(50.0, comparator.apiDiff.pdiff);
    }

    @Test
    void identifiesAMethodReturnTypeChangeWithinAnExistingPackage() throws Exception {
        API oldApi = ApiFiles.read(directory, "Baseline",
                ApiFiles.packageWithClass("example.catalog", ApiFiles.method("lookup", "int")));
        API newApi = ApiFiles.read(directory, "Current",
                ApiFiles.packageWithClass("example.catalog", ApiFiles.method("lookup", "long")));
        APIComparator comparator = new APIComparator();

        comparator.compareAPIs(oldApi, newApi);

        assertTrue(comparator.apiDiff.packagesAdded.isEmpty());
        assertTrue(comparator.apiDiff.packagesRemoved.isEmpty());
        assertEquals(1, comparator.apiDiff.packagesChanged.size());
        assertTrue(comparator.apiDiff.pdiff > 0.0);
        assertTrue(comparator.apiDiff.pdiff <= 100.0);
    }
}
