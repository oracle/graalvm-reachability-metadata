/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package at_yawk_lz4.lz4_java;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises LZ4 block streams by persisting and replaying a binary billing journal.
/// Replaying records across compression block boundaries rebuilds each customer's billed balance.
public class BillingJournalBlockStreamTest {
    @TempDir
    Path directory;

    @Test
    @Timeout(60)
    public void rebuildsCustomerBalancesFromCompressedJournal() throws IOException {
        Path journal = directory.resolve("billing-journal.lz4block");
        List<String> customers = List.of("customer-north", "customer-east", "customer-west");
        int recordCount = 1200;

        try (DataOutputStream output = new DataOutputStream(
                new LZ4BlockOutputStream(Files.newOutputStream(journal), 4096))) {
            output.writeUTF("billing-journal");
            output.writeInt(recordCount);
            for (int invoice = 0; invoice < recordCount; invoice++) {
                int customerIndex = invoice % customers.size();
                output.writeInt(invoice);
                output.writeUTF(customers.get(customerIndex));
                output.writeLong(250L + 50L * customerIndex);
            }
        }

        Map<String, Long> balances = new LinkedHashMap<>();
        try (DataInputStream input = new DataInputStream(
                new LZ4BlockInputStream(Files.newInputStream(journal)))) {
            assertEquals("billing-journal", input.readUTF());
            int storedRecordCount = input.readInt();
            assertEquals(recordCount, storedRecordCount);
            for (int invoice = 0; invoice < storedRecordCount; invoice++) {
                assertEquals(invoice, input.readInt(), "Invoice sequence must survive replay");
                String customer = input.readUTF();
                long amountCents = input.readLong();
                assertEquals(customers.get(invoice % customers.size()), customer);
                assertEquals(250L + 50L * (invoice % customers.size()), amountCents);
                balances.merge(customer, amountCents, Long::sum);
            }
            assertEquals(-1, input.read(), "The journal must end after its declared invoices");
        }

        assertEquals(Map.of(
                "customer-north", 100000L,
                "customer-east", 120000L,
                "customer-west", 140000L), balances);
    }
}
