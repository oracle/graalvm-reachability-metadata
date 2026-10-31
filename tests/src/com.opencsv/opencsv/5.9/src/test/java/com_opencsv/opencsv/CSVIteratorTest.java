/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.CSVIterator;
import com.opencsv.CSVReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(120)
public class CSVIteratorTest {
    @Test
    void iteratesRecordsButDoesNotAllowRemoval() throws Exception {
        try (CSVReader reader = new CSVReader(new StringReader("alpha,one\nbeta,two\n"))) {
            CSVIterator iterator = new CSVIterator(reader);
            iterator.setErrorLocale(Locale.US);
            assertThat(iterator.next()).containsExactly("alpha", "one");
            assertThatThrownBy(iterator::remove)
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("read-only iterator");
            assertThat(iterator.next()).containsExactly("beta", "two");
            assertThat(iterator.hasNext()).isFalse();
        }
    }
}
