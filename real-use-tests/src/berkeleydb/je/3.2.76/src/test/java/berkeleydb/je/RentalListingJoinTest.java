/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.JoinCursor;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises JE join cursors to find rental listings matching both a city and a property type.
@Timeout(60)
public class RentalListingJoinTest {
    @TempDir
    Path home;

    @Test
    public void intersectsSearchFacetsAndReflectsListingChangesAfterRestart() throws DatabaseException {
        Listing studio = new Listing("Cedar", "apartment", "Sunny studio");
        Listing house = new Listing("Cedar", "house", "Garden house");
        Listing loft = new Listing("Riverton", "apartment", "Riverside loft");
        Listing duplex = new Listing("Cedar", "apartment", "Two-bedroom duplex");
        Listing relocated = new Listing("Riverton", "apartment", "Two-bedroom duplex");

        try (RentalDirectory directory = new RentalDirectory(home, true)) {
            directory.put("listing-1", studio);
            directory.put("listing-2", house);
            directory.put("listing-3", loft);
            directory.put("listing-4", duplex);
            assertEquals(Map.of("listing-1", studio, "listing-4", duplex),
                    directory.search("Cedar", "apartment"));
            assertEquals(Map.of("listing-2", house), directory.search("Cedar", "house"));
            // Both facets exist, but their intersection contains no listings.
            assertEquals(Map.of(), directory.search("Riverton", "house"));
            assertEquals(Map.of(), directory.search("Unknown city", "apartment"));
            assertEquals(Map.of(), directory.search("Cedar", "cabin"));

            directory.put("listing-4", relocated);
            assertEquals(Map.of("listing-1", studio), directory.search("Cedar", "apartment"));
            assertEquals(Map.of("listing-3", loft, "listing-4", relocated),
                    directory.search("Riverton", "apartment"));
            assertEquals(OperationStatus.SUCCESS, directory.listings.delete(null, entry("listing-1")));
            assertEquals(Map.of(), directory.search("Cedar", "apartment"));
        }

        try (RentalDirectory directory = new RentalDirectory(home, false)) {
            assertEquals(Map.of("listing-3", loft, "listing-4", relocated),
                    directory.search("Riverton", "apartment"));
            assertEquals(Map.of("listing-2", house), directory.search("Cedar", "house"));
            assertEquals(Map.of(), directory.search("Cedar", "apartment"));
            assertEquals(Map.of(), directory.search("Riverton", "house"));
        }
    }

    private static DatabaseEntry entry(String value) {
        DatabaseEntry result = new DatabaseEntry();
        StringBinding.stringToEntry(value, result);
        return result;
    }

    private record Listing(String city, String propertyType, String headline) {
    }

    private static final class ListingBinding extends TupleBinding {
        @Override
        public Object entryToObject(TupleInput input) {
            return new Listing(input.readString(), input.readString(), input.readString());
        }

        @Override
        public void objectToEntry(Object value, TupleOutput output) {
            Listing listing = (Listing) value;
            output.writeString(listing.city());
            output.writeString(listing.propertyType());
            output.writeString(listing.headline());
        }
    }

    private static final class RentalDirectory implements AutoCloseable {
        private final Environment environment;
        private final Database listings;
        private final SecondaryDatabase byCity;
        private final SecondaryDatabase byPropertyType;
        private final ListingBinding binding = new ListingBinding();

        private RentalDirectory(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setLockTimeout(10_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            try {
                listings = environment.openDatabase(null, "rental-listings", databaseConfig);
                try {
                    byCity = openFacet("listings-by-city", create, true);
                    try {
                        byPropertyType = openFacet("listings-by-type", create, false);
                    } catch (DatabaseException | RuntimeException | Error failure) {
                        byCity.close();
                        throw failure;
                    }
                } catch (DatabaseException | RuntimeException | Error failure) {
                    listings.close();
                    throw failure;
                }
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private SecondaryDatabase openFacet(String name, boolean create, boolean city) throws DatabaseException {
            SecondaryConfig config = new SecondaryConfig();
            config.setAllowCreate(create);
            config.setSortedDuplicates(true);
            config.setKeyCreator((secondary, primaryKey, data, secondaryKey) -> {
                Listing listing = (Listing) binding.entryToObject(data);
                StringBinding.stringToEntry(city ? listing.city() : listing.propertyType(), secondaryKey);
                return true;
            });
            return environment.openSecondaryDatabase(null, name, listings, config);
        }

        private void put(String id, Listing listing) throws DatabaseException {
            DatabaseEntry data = new DatabaseEntry();
            binding.objectToEntry(listing, data);
            assertEquals(OperationStatus.SUCCESS, listings.put(null, entry(id), data));
        }

        private Map<String, Listing> search(String city, String propertyType) throws DatabaseException {
            Map<String, Listing> matches = new HashMap<>();
            SecondaryCursor cities = byCity.openSecondaryCursor(null, null);
            try {
                SecondaryCursor types = byPropertyType.openSecondaryCursor(null, null);
                try {
                    if (!position(cities, city) || !position(types, propertyType)) {
                        return matches;
                    }
                    // JE intersects the positioned secondary cursors and fetches primary records.
                    JoinCursor join = listings.join(new Cursor[]{cities, types}, null);
                    try {
                        DatabaseEntry id = new DatabaseEntry();
                        DatabaseEntry data = new DatabaseEntry();
                        OperationStatus status = join.getNext(id, data, LockMode.DEFAULT);
                        while (status == OperationStatus.SUCCESS) {
                            assertNull(matches.put(StringBinding.entryToString(id),
                                    (Listing) binding.entryToObject(data)));
                            status = join.getNext(id, data, LockMode.DEFAULT);
                        }
                        assertEquals(OperationStatus.NOTFOUND, status);
                    } finally {
                        join.close();
                    }
                } finally {
                    types.close();
                }
            } finally {
                cities.close();
            }
            // Join order is not part of the application's search-result contract.
            return matches;
        }

        private static boolean position(SecondaryCursor cursor, String value) throws DatabaseException {
            OperationStatus status = cursor.getSearchKey(entry(value), new DatabaseEntry(),
                    new DatabaseEntry(), LockMode.DEFAULT);
            if (status == OperationStatus.NOTFOUND) {
                return false;
            }
            assertEquals(OperationStatus.SUCCESS, status);
            return true;
        }

        @Override
        public void close() throws DatabaseException {
            try {
                byPropertyType.close();
            } finally {
                try {
                    byCity.close();
                } finally {
                    try {
                        listings.close();
                    } finally {
                        environment.close();
                    }
                }
            }
        }
    }
}
