/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.collections.StoredIterator;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises JE's Collections API to maintain a persistent, time-ordered meeting schedule.
@Timeout(60)
public class PersistentScheduleMapTest {
    @TempDir
    Path home;

    @Test
    public void editsScheduleThroughSortedMapViewsAndReopensIt() throws DatabaseException {
        Booking planning = new Booking("Planning", 6);
        Booking interview = new Booking("Interview", 2);
        Booking review = new Booking("Design review", 4);
        Booking workshop = new Booking("Workshop", 12);

        try (ScheduleStore store = new ScheduleStore(home, true)) {
            SortedMap<Integer, Booking> schedule = store.bookings;
            // Insert out of order: the database, not the insertion sequence, orders the day.
            assertNull(schedule.put(780, workshop));
            assertNull(schedule.put(660, review));
            assertNull(schedule.put(540, planning));
            assertNull(schedule.put(570, interview));
            assertEquals(540, schedule.firstKey());
            assertEquals(780, schedule.lastKey());
            assertEquals(List.of(planning, interview, review, workshop), orderedBookings(schedule));

            SortedMap<Integer, Booking> morning = schedule.subMap(540, 720);
            assertEquals(List.of(planning, interview, review), orderedBookings(morning));

            // A cancellation through a range view removes the underlying database record.
            assertEquals(interview, morning.remove(570));
            assertFalse(schedule.containsKey(570));
            Booking revisedReview = new Booking("Design review", 5);
            assertEquals(review, schedule.put(660, revisedReview));
            assertEquals(revisedReview, morning.get(660));

            Booking lunch = new Booking("Team lunch", 8);
            assertNull(schedule.put(720, lunch));
            assertEquals(List.of(planning, revisedReview), orderedBookings(morning));
            assertEquals(List.of(lunch, workshop), orderedBookings(schedule.tailMap(720)));
        }

        // Bindings reconstruct application values from disk, with no Java serialization.
        try (ScheduleStore store = new ScheduleStore(home, false)) {
            SortedMap<Integer, Booking> schedule = store.bookings;
            assertEquals(List.of(planning, new Booking("Design review", 5),
                    new Booking("Team lunch", 8), workshop), orderedBookings(schedule));
            assertFalse(schedule.containsKey(570));
            assertEquals(List.of(planning), orderedBookings(schedule.headMap(660)));
            assertEquals(540, schedule.firstKey());
            assertEquals(780, schedule.lastKey());
        }
    }

    private static List<Booking> orderedBookings(SortedMap<Integer, Booking> schedule) {
        List<Booking> result = new ArrayList<>();
        Iterator<Booking> iterator = schedule.values().iterator();
        try {
            while (iterator.hasNext()) {
                result.add(iterator.next());
            }
        } finally {
            StoredIterator.close(iterator);
        }
        return result;
    }

    private record Booking(String title, int attendees) {
    }

    private static final class BookingBinding extends TupleBinding {
        @Override
        public Object entryToObject(TupleInput input) {
            return new Booking(input.readString(), input.readInt());
        }

        @Override
        public void objectToEntry(Object value, TupleOutput output) {
            Booking booking = (Booking) value;
            output.writeString(booking.title());
            output.writeInt(booking.attendees());
        }
    }

    private static final class ScheduleStore implements AutoCloseable {
        private final Environment environment;
        private final Database database;
        private final SortedMap<Integer, Booking> bookings;

        @SuppressWarnings("unchecked")
        private ScheduleStore(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setLockTimeout(10_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);

            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            try {
                database = environment.openDatabase(null, "meeting-schedule", databaseConfig);
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
            // This JE version predates generic collection signatures.
            bookings = new StoredSortedMap(database, new IntegerBinding(), new BookingBinding(), true);
        }

        @Override
        public void close() throws DatabaseException {
            try {
                database.close();
            } finally {
                environment.close();
            }
        }
    }
}
