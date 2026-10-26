/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises JNA's explicit native function lookup and invocation to select backup files with POSIX filename patterns.
 */
public class NativeBackupSelectionTest {

    @Test
    @Timeout(60)
    public void selectsInvoiceBackupsUsingIncludeAndExcludePatterns() {
        NativeLibrary library = NativeLibrary.getInstance(Platform.C_LIBRARY_NAME);
        Function matcher = library.getFunction("fnmatch");
        BackupFilter filter = new BackupFilter(matcher);
        List<String> candidates = List.of(
                "invoice-17.csv",
                "invoice-02.csv",
                "invoice-AA.csv",
                "invoice-04.csv",
                "invoice-17.csv.tmp",
                "INVOICE-05.csv",
                "notes.txt");

        List<String> selected = filter.select(candidates, "invoice-[0-9][0-9].csv", "invoice-0[1-3].csv");
        assertEquals(List.of("invoice-04.csv", "invoice-17.csv"), selected,
                "The backup manifest must contain only numeric invoices outside the excluded range");

        List<String> revised = filter.select(candidates, "invoice-??.csv", "invoice-0?.csv");
        assertEquals(List.of("invoice-17.csv", "invoice-AA.csv"), revised,
                "Changing the configured rules must allow two-character identifiers and exclude early invoices");
        assertEquals(List.of(), filter.select(candidates, "archive-*.csv", "*.tmp"),
                "An unmatched include rule must produce an empty backup manifest");
    }

    private static final class BackupFilter {
        private final Function matcher;

        private BackupFilter(Function matcher) {
            this.matcher = matcher;
        }

        private List<String> select(List<String> candidates, String include, String exclude) {
            return candidates.stream()
                    .filter(name -> matches(include, name) && !matches(exclude, name))
                    .sorted()
                    .toList();
        }

        private boolean matches(String pattern, String name) {
            // POSIX fnmatch returns zero on a match; flags zero keeps matching case-sensitive.
            return matcher.invokeInt(new Object[]{pattern, name, 0}) == 0;
        }
    }
}
