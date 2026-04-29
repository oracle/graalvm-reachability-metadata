/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.apache.calcite.util.Util;
import org.junit.jupiter.api.Test;

public class UtilTest {
    @Test
    void printRendersPublicInstanceFields() {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);

        Util.print(writer, new PrintableNode());
        writer.flush();

        assertThat(buffer.toString())
                .contains(PrintableNode.class.getName())
                .contains("name=\"calcite\"")
                .contains("ordinal=35")
                .doesNotContain("ignoredStaticField");
    }

    public static class PrintableNode {
        public String name = "calcite";
        public int ordinal = 35;
        public static String ignoredStaticField = "static";
    }
}
