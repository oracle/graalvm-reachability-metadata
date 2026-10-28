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
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JE nonblocking record locking to reject competing seat checkouts and safely retry reservations.
@Timeout(60)
public class SeatReservationContentionTest {
    @TempDir
    Path home;

    @Test
    public void reportsBusySeatsAndRetriesAfterCheckoutCommitOrCancellation() throws DatabaseException {
        try (BoxOffice office = new BoxOffice(home, true)) {
            office.addSeat("A-12");
            office.addSeat("A-13");
            office.addSeat("A-14");

            // Overlap transactions explicitly, so contention does not depend on thread scheduling.
            try (Checkout firstBuyer = office.beginCheckout()) {
                assertEquals(ReservationResult.RESERVED, firstBuyer.reserve("A-12", "Ada"));
                assertEquals(ReservationResult.BUSY, office.reserveNow("A-12", "Ben"));
                // A checkout on another seat can still complete while the first seat is locked.
                assertEquals(ReservationResult.RESERVED, office.reserveNow("A-13", "Cleo"));
                firstBuyer.confirm();
            }
            // Retrying after a committed checkout must not overwrite the winning buyer.
            assertEquals(ReservationResult.TAKEN, office.reserveNow("A-12", "Ben"));
            assertEquals("Ada", office.owner("A-12"));
            assertEquals("Cleo", office.owner("A-13"));

            try (Checkout cancelledBuyer = office.beginCheckout()) {
                assertEquals(ReservationResult.RESERVED, cancelledBuyer.reserve("A-14", "Dara"));
                assertEquals(ReservationResult.BUSY, office.reserveNow("A-14", "Eli"));
                // Leaving without confirmation cancels the pending checkout and releases its lock.
            }
            assertEquals("", office.owner("A-14"));
            assertEquals(ReservationResult.RESERVED, office.reserveNow("A-14", "Eli"));
            assertEquals("Eli", office.owner("A-14"));
        }

        try (BoxOffice office = new BoxOffice(home, false)) {
            assertEquals("Ada", office.owner("A-12"));
            assertEquals("Cleo", office.owner("A-13"));
            assertEquals("Eli", office.owner("A-14"));
            assertEquals(ReservationResult.TAKEN, office.reserveNow("A-14", "Dara"));
            assertEquals("Eli", office.owner("A-14"));
        }
    }

    private enum ReservationResult {
        RESERVED, TAKEN, BUSY
    }

    private static DatabaseEntry entry(String text) {
        DatabaseEntry entry = new DatabaseEntry();
        StringBinding.stringToEntry(text, entry);
        return entry;
    }

    private static final class Checkout implements AutoCloseable {
        private final Database seats;
        private Transaction transaction;

        private Checkout(Database seats, Transaction transaction) {
            this.seats = seats;
            this.transaction = transaction;
        }

        private ReservationResult reserve(String seat, String buyer) throws DatabaseException {
            DatabaseEntry owner = new DatabaseEntry();
            // Take the write-intent lock before checking availability to prevent double booking.
            assertEquals(OperationStatus.SUCCESS, seats.get(transaction, entry(seat), owner, LockMode.RMW));
            if (!StringBinding.entryToString(owner).isEmpty()) {
                return ReservationResult.TAKEN;
            }
            assertEquals(OperationStatus.SUCCESS, seats.put(transaction, entry(seat), entry(buyer)));
            return ReservationResult.RESERVED;
        }

        private void confirm() throws DatabaseException {
            transaction.commitSync();
            transaction = null;
        }

        @Override
        public void close() throws DatabaseException {
            if (transaction != null) {
                transaction.abort();
                transaction = null;
            }
        }
    }

    private static final class BoxOffice implements AutoCloseable {
        private final Environment environment;
        private final Database seats;

        private BoxOffice(Path home, boolean create) throws DatabaseException {
            EnvironmentConfig environmentConfig = new EnvironmentConfig();
            environmentConfig.setAllowCreate(create);
            environmentConfig.setTransactional(true);
            environmentConfig.setLockTimeout(10_000_000L);
            environmentConfig.setTxnTimeout(30_000_000L);
            environment = new Environment(home.toFile(), environmentConfig);
            DatabaseConfig databaseConfig = new DatabaseConfig();
            databaseConfig.setAllowCreate(create);
            databaseConfig.setTransactional(true);
            try {
                seats = environment.openDatabase(null, "seat-reservations", databaseConfig);
            } catch (DatabaseException | RuntimeException | Error failure) {
                environment.close();
                throw failure;
            }
        }

        private void addSeat(String seat) throws DatabaseException {
            assertEquals(OperationStatus.SUCCESS, seats.putNoOverwrite(null, entry(seat), entry("")));
        }

        private Checkout beginCheckout() throws DatabaseException {
            TransactionConfig config = new TransactionConfig();
            // Interactive checkouts report contention immediately instead of waiting for a timeout.
            config.setNoWait(true);
            return new Checkout(seats, environment.beginTransaction(null, config));
        }

        private ReservationResult reserveNow(String seat, String buyer) throws DatabaseException {
            try (Checkout checkout = beginCheckout()) {
                ReservationResult result = checkout.reserve(seat, buyer);
                if (result == ReservationResult.RESERVED) {
                    checkout.confirm();
                }
                return result;
            } catch (LockNotGrantedException contention) {
                // The failed transaction has been aborted before the caller can retry.
                return ReservationResult.BUSY;
            }
        }

        private String owner(String seat) throws DatabaseException {
            DatabaseEntry owner = new DatabaseEntry();
            assertEquals(OperationStatus.SUCCESS, seats.get(null, entry(seat), owner, LockMode.DEFAULT));
            return StringBinding.entryToString(owner);
        }

        @Override
        public void close() throws DatabaseException {
            try {
                seats.close();
            } finally {
                environment.close();
            }
        }
    }
}
