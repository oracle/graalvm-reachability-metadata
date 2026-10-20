/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.internal.StringUtil;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the string utility subsystem of netty-common: `StringUtil` RFC 4180 CSV escaping and
 * field splitting, hexadecimal encoding and hex-dump decoding, and the small text helpers
 * (`join`, `trimOws`, `substringAfter`, `simpleClassName`) applications reach for alongside them.
 *
 * The scenario is a ledger exchange service that exports entries as CSV rows with a hex SHA-256
 * fingerprint per row, imports a partner's CSV file back, and rejects malformed rows.
 */
class LedgerCsvExchangeStringUtilTest {

    @Test
    void exportsEntriesAsCsvRowsQuotingOnlyTheFieldsThatNeedIt() {
        List<LedgerEntry> entries = List.of(
                new LedgerEntry("INV-1001", "Office chairs", "1250.00"),
                new LedgerEntry("INV-1002", "Desks, standing", "980.50"),
                new LedgerEntry("INV-1003", "Monitor \"UltraWide\" 34in", "410.00"),
                new LedgerEntry("INV-1004", "Cables\nand adapters", "35.99"));

        List<String> rows = new ArrayList<>();
        for (LedgerEntry entry : entries) {
            rows.add(LedgerCsv.exportRow(entry));
        }

        assertThat(rows).containsExactly(
                "INV-1001,Office chairs,1250.00",
                "INV-1002,\"Desks, standing\",980.50",
                "INV-1003,\"Monitor \"\"UltraWide\"\" 34in\",410.00",
                "INV-1004,\"Cables\nand adapters\",35.99");

        assertThat(StringUtil.escapeCsv("\"already quoted\"").toString())
                .isEqualTo("\"already quoted\"");
        assertThat(StringUtil.escapeCsv("   padded memo  ", true).toString())
                .isEqualTo("padded memo");
        assertThat(StringUtil.escapeCsv("   padded memo  ").toString())
                .isEqualTo("   padded memo  ");
    }

    @Test
    void importsAPartnerFileBackIntoTheSameEntries() {
        String partnerFile = """
                INV-2001,"Paper, A4 (5 reams)",42.00
                INV-2002,"Toner ""Black"" XL",89.90
                INV-2003,,0.00
                INV-2004,"Two-line
                description",12.30
                """;

        List<LedgerEntry> imported = LedgerCsv.importFile(partnerFile);

        assertThat(imported).containsExactly(
                new LedgerEntry("INV-2001", "Paper, A4 (5 reams)", "42.00"),
                new LedgerEntry("INV-2002", "Toner \"Black\" XL", "89.90"),
                new LedgerEntry("INV-2003", "", "0.00"),
                new LedgerEntry("INV-2004", "Two-line\ndescription", "12.30"));

        for (LedgerEntry entry : imported) {
            assertThat(LedgerCsv.parseRow(LedgerCsv.exportRow(entry))).isEqualTo(entry);
        }
        assertThat(StringUtil.unescapeCsv("\"Paper, A4 (5 reams)\"").toString())
                .isEqualTo("Paper, A4 (5 reams)");
        assertThat(StringUtil.unescapeCsv("plain").toString()).isEqualTo("plain");
    }

    @Test
    void rejectsMalformedRowsFromThePartner() {
        assertThatThrownBy(() -> LedgerCsv.parseRow("INV-3001,Broken \"quote\",1.00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LedgerCsv.parseRow("INV-3002,\"never closed,1.00"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StringUtil.unescapeCsv("a,b"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LedgerCsv.parseRow("INV-3003,only two fields"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("LedgerEntry row needs 3 fields but had 2");
    }

    @Test
    void fingerprintsRowsAsHexAndDecodesThemBack() throws NoSuchAlgorithmException {
        String row = LedgerCsv.exportRow(new LedgerEntry("INV-1001", "Office chairs", "1250.00"));
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(row.getBytes(StandardCharsets.UTF_8));

        String fingerprint = StringUtil.toHexStringPadded(digest);

        assertThat(fingerprint).hasSize(64).matches("[0-9a-f]+");
        assertThat(StringUtil.decodeHexDump(fingerprint)).isEqualTo(digest);
        assertThat(StringUtil.decodeHexDump("xx" + fingerprint + "yy", 2, 64)).isEqualTo(digest);
        assertThat(StringUtil.decodeHexByte(fingerprint, 0)).isEqualTo(digest[0]);

        byte[] sequenceNumber = {0x00, 0x00, 0x01, 0x0a, (byte) 0xff};
        assertThat(StringUtil.toHexStringPadded(sequenceNumber)).isEqualTo("0000010aff");
        assertThat(StringUtil.toHexString(sequenceNumber)).isEqualTo("10aff");
        assertThat(StringUtil.byteToHexStringPadded(7)).isEqualTo("07");
        assertThat(StringUtil.byteToHexString(7)).isEqualTo("7");
        assertThat(StringUtil.decodeHexNibble('c')).isEqualTo(12);
        assertThat(StringUtil.decodeHexNibble('g')).isEqualTo(-1);
        assertThatThrownBy(() -> StringUtil.decodeHexDump("abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesTheExportHeadersWithTheTextHelpers() {
        String header = "Content-Disposition: attachment; filename=ledger.csv";
        String value = StringUtil.substringAfter(header, ':');
        assertThat(StringUtil.trimOws(value).toString())
                .isEqualTo("attachment; filename=ledger.csv");
        assertThat(StringUtil.substringBefore(header, ':')).isEqualTo("Content-Disposition");
        assertThat(StringUtil.substringAfter(header, '#')).isNull();
        assertThat(StringUtil.commonSuffixOfLength("ledger.csv", "partner.csv", 4)).isTrue();
        assertThat(StringUtil.commonSuffixOfLength("ledger.csv", "ledger.tsv", 4)).isFalse();

        assertThat(StringUtil.join(", ", List.of("id", "description", "amount")).toString())
                .isEqualTo("id, description, amount");
        assertThat(StringUtil.isNullOrEmpty("")).isTrue();
        assertThat(StringUtil.isNullOrEmpty(null)).isTrue();
        assertThat(StringUtil.indexOfNonWhiteSpace("   INV", 0)).isEqualTo(3);
        assertThat(StringUtil.endsWith("ledger.csv", 'v')).isTrue();

        assertThat(StringUtil.simpleClassName(LedgerEntry.class))
                .isEqualTo("LedgerCsvExchangeStringUtilTest$LedgerEntry");
        assertThat(StringUtil.simpleClassName(new LedgerEntry("a", "b", "c")))
                .isEqualTo("LedgerCsvExchangeStringUtilTest$LedgerEntry");
    }

    /// One ledger line as the exchange service stores it.
    private record LedgerEntry(String id, String description, String amount) {
    }

    /// The application's CSV codec for ledger entries, built on `StringUtil`.
    private static final class LedgerCsv {

        private static final int FIELD_COUNT = 3;

        private LedgerCsv() {
        }

        static String exportRow(LedgerEntry entry) {
            List<CharSequence> fields = List.of(
                    StringUtil.escapeCsv(entry.id()),
                    StringUtil.escapeCsv(entry.description()),
                    StringUtil.escapeCsv(entry.amount()));
            return StringUtil.join(String.valueOf(StringUtil.COMMA), fields).toString();
        }

        static LedgerEntry parseRow(CharSequence row) {
            List<CharSequence> fields = StringUtil.unescapeCsvFields(row);
            if (fields.size() != FIELD_COUNT) {
                throw new IllegalStateException("LedgerEntry row needs " + FIELD_COUNT
                        + " fields but had " + fields.size());
            }
            return new LedgerEntry(
                    fields.get(0).toString(), fields.get(1).toString(), fields.get(2).toString());
        }

        /// Splits a file into rows at line feeds that sit outside quoted fields.
        static List<LedgerEntry> importFile(String file) {
            List<LedgerEntry> entries = new ArrayList<>();
            StringBuilder row = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < file.length(); i++) {
                char c = file.charAt(i);
                if (c == StringUtil.DOUBLE_QUOTE) {
                    quoted = !quoted;
                }
                if (c == StringUtil.LINE_FEED && !quoted) {
                    entries.add(parseRow(row));
                    row.setLength(0);
                } else {
                    row.append(c);
                }
            }
            assertThat(row).as("trailing partial row").isEmpty();
            return entries;
        }
    }
}
