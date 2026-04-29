/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.util.Util;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilTest {
    @Test
    void printsPublicInstanceFields() {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);

        Util.print(writer, new PrintableRecord());
        writer.flush();

        assertThat(buffer.toString())
                .contains(PrintableRecord.class.getName())
                .contains("label=\"metadata\"")
                .contains("count=3")
                .doesNotContain("IGNORED_STATIC_FIELD");
    }

    public static class PrintableRecord {
        public static final String IGNORED_STATIC_FIELD = "ignored";

        public String label = "metadata";
        public int count = 3;
    }
}
