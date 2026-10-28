/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JE secondary indexes to browse a product catalog by category as products change.
@Timeout(60)
public class ProductCategoryIndexTest {
    @TempDir
    Path home;

    @Test
    public void buildsCategoryIndexAndKeepsItInSyncWithCatalogEdits() throws DatabaseException {
        EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        environmentConfig.setLockTimeout(10_000_000L);
        Environment environment = new Environment(home.toFile(), environmentConfig);
        try {
            DatabaseConfig catalogConfig = new DatabaseConfig();
            catalogConfig.setAllowCreate(true);
            Database catalog = environment.openDatabase(null, "products", catalogConfig);
            try {
                // The catalog predates category browsing; opening the index must populate it.
                putProduct(catalog, "coffee", "groceries");
                putProduct(catalog, "tea", "groceries");
                putProduct(catalog, "mug", "housewares");

                SecondaryConfig categoryConfig = new SecondaryConfig();
                categoryConfig.setAllowCreate(true);
                categoryConfig.setAllowPopulate(true);
                categoryConfig.setSortedDuplicates(true);
                categoryConfig.setKeyCreator((secondary, primaryKey, productData, categoryKey) -> {
                    StringBinding.stringToEntry(StringBinding.entryToString(productData), categoryKey);
                    return true;
                });
                SecondaryDatabase categories = environment.openSecondaryDatabase(
                        null, "products-by-category", catalog, categoryConfig);
                try {
                    assertEquals(List.of("coffee", "tea"), productsInCategory(categories, "groceries"));
                    assertEquals(List.of("mug"), productsInCategory(categories, "housewares"));
                    assertEquals(List.of(), productsInCategory(categories, "clearance"));

                    putProduct(catalog, "cocoa", "groceries");
                    assertEquals(List.of("cocoa", "coffee", "tea"), productsInCategory(categories, "groceries"));

                    // Reclassifying a product removes its old index entry and creates the new one.
                    putProduct(catalog, "tea", "clearance");
                    assertEquals(List.of("cocoa", "coffee"), productsInCategory(categories, "groceries"));
                    assertEquals(List.of("tea"), productsInCategory(categories, "clearance"));

                    assertEquals(OperationStatus.SUCCESS, catalog.delete(null, entry("coffee")));
                    assertEquals(List.of("cocoa"), productsInCategory(categories, "groceries"));
                    assertEquals(OperationStatus.SUCCESS, catalog.delete(null, entry("tea")));
                    assertEquals(List.of(), productsInCategory(categories, "clearance"));
                    assertEquals(List.of("mug"), productsInCategory(categories, "housewares"));
                } finally {
                    categories.close();
                }
            } finally {
                catalog.close();
            }
        } finally {
            environment.close();
        }
    }

    private static void putProduct(Database catalog, String sku, String category) throws DatabaseException {
        assertEquals(OperationStatus.SUCCESS, catalog.put(null, entry(sku), entry(category)));
    }

    private static List<String> productsInCategory(SecondaryDatabase categories, String category)
            throws DatabaseException {
        List<String> products = new ArrayList<>();
        SecondaryCursor cursor = categories.openSecondaryCursor(null, null);
        try {
            DatabaseEntry categoryKey = entry(category);
            DatabaseEntry productKey = new DatabaseEntry();
            DatabaseEntry productData = new DatabaseEntry();
            OperationStatus status = cursor.getSearchKey(categoryKey, productKey, productData, LockMode.DEFAULT);
            while (status == OperationStatus.SUCCESS) {
                assertEquals(category, StringBinding.entryToString(productData));
                products.add(StringBinding.entryToString(productKey));
                status = cursor.getNextDup(categoryKey, productKey, productData, LockMode.DEFAULT);
            }
            assertEquals(OperationStatus.NOTFOUND, status);
        } finally {
            cursor.close();
        }
        return products;
    }

    private static DatabaseEntry entry(String value) {
        DatabaseEntry entry = new DatabaseEntry();
        StringBinding.stringToEntry(value, entry);
        return entry;
    }
}
