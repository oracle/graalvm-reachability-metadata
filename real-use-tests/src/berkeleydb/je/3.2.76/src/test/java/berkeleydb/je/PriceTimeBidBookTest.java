/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package berkeleydb.je;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JE custom B-tree comparators to persist a bid book ordered by price and arrival priority.
@Timeout(60)
public class PriceTimeBidBookTest {
    @TempDir
    Path home;

    @Test
    public void ranksBidsCancelsTheLeaderAndRetainsPriorityAfterRestart() throws DatabaseException {
        Bid low = new Bid(9800, 1L, "Ada");
        Bid early = new Bid(10500, 2L, "Ben");
        Bid late = new Bid(10500, 3L, "Cleo");
        Bid middle = new Bid(10200, 4L, "Dara");
        Bid highest = new Bid(11000, 5L, "Eli");
        Bid later = new Bid(10500, 6L, "Fay");

        try (BidBook book = new BidBook(home, true)) {
            // Neither insertion order nor ascending byte order is the auction's priority order.
            book.place(middle);
            book.place(late);
            book.place(low);
            book.place(early);
            assertEquals(List.of(early, late, middle, low), book.rankedBids());

            book.cancel(early);
            assertEquals(List.of(late, middle, low), book.rankedBids());
            book.place(highest);
            assertEquals(List.of(highest, late, middle, low), book.rankedBids());
        }

        // Reopening supplies no comparator: JE restores the ordering saved with the database.
        try (BidBook book = new BidBook(home, false)) {
            assertEquals(List.of(highest, late, middle, low), book.rankedBids());
            book.place(later);
            assertEquals(List.of(highest, late, later, middle, low), book.rankedBids());
            book.cancel(highest);
            assertEquals(List.of(late, later, middle, low), book.rankedBids());
        }

        try (BidBook book = new BidBook(home, false)) {
            assertEquals(List.of(late, later, middle, low), book.rankedBids());
        }
    }

    private record Bid(int priceCents, long arrival, String bidder) {
    }

    public static final class PriceTimeComparator implements Comparator<byte[]> {
        public PriceTimeComparator() {
            // JE constructs this stateless comparator when reopening the persisted database.
        }

        @Override
        public int compare(byte[] left, byte[] right) {
            TupleInput leftKey = new TupleInput(left);
            TupleInput rightKey = new TupleInput(right);
            int priceOrder = Integer.compare(rightKey.readInt(), leftKey.readInt());
            if (priceOrder != 0) {
                return priceOrder;
            }
            // Arrival is an application-assigned ordinal, not a wall-clock timestamp.
            return Long.compare(leftKey.readLong(), rightKey.readLong());
        }
    }

    private static final class BidBook implements AutoCloseable {
        private final Environment environment;
        private final Database database;

        private BidBook(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setLockTimeout(10_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);

            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            if (create) {
                databaseConfig.setBtreeComparator(PriceTimeComparator.class);
            }
            try {
                database = environment.openDatabase(null, "auction-bids", databaseConfig);
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private void place(Bid bid) throws DatabaseException {
            DatabaseEntry bidder = new DatabaseEntry();
            StringBinding.stringToEntry(bid.bidder(), bidder);
            assertEquals(OperationStatus.SUCCESS, database.putNoOverwrite(null, key(bid), bidder));
        }

        private void cancel(Bid bid) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS, database.delete(null, key(bid)));
        }

        private List<Bid> rankedBids() throws DatabaseException {
            List<Bid> bids = new ArrayList<>();
            Cursor cursor = database.openCursor(null, null);
            try {
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();
                OperationStatus status = cursor.getFirst(key, data, LockMode.DEFAULT);
                while (status == OperationStatus.SUCCESS) {
                    TupleInput input = new TupleInput(key.getData(), key.getOffset(), key.getSize());
                    bids.add(new Bid(input.readInt(), input.readLong(), StringBinding.entryToString(data)));
                    status = cursor.getNext(key, data, LockMode.DEFAULT);
                }
                assertEquals(OperationStatus.NOTFOUND, status);
            } finally {
                cursor.close();
            }
            return bids;
        }

        private static DatabaseEntry key(Bid bid) {
            TupleOutput output = new TupleOutput();
            output.writeInt(bid.priceCents());
            output.writeLong(bid.arrival());
            return new DatabaseEntry(output.toByteArray());
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
