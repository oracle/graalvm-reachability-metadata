/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_antlr.antlr;

import java.util.Collections;

import org.antlr.tool.Grammar;
import org.antlr.tool.GrammarReport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GrammarReportTest {
    private static final int REPORT_DATA_FIELD_COUNT = 31;

    @Test
    void toNotifyStringSerializesGrammarReportDataFields() throws Exception {
        Grammar grammar = newSimpleReportGrammar();

        String notifyDataLine = new GrammarReport(grammar).toNotifyString();

        assertThat(notifyDataLine.split("\t", -1)).hasSizeGreaterThanOrEqualTo(REPORT_DATA_FIELD_COUNT);
        assertThat(notifyDataLine)
                .contains("\tSimpleReport\t")
                .contains("\tparser\t")
                .contains("\tJava\t");
    }

    @Test
    void toStringDecodesTabSeparatedNotifyDataLine() {
        String notifyDataLine = String.join("\t", Collections.nCopies(128, "0"));

        String report = GrammarReport.toString(notifyDataLine);

        assertThat(report)
                .contains("ANTLR Grammar Report; Stats Version")
                .contains("Grammar:")
                .contains("Type:")
                .contains("Target language:")
                .contains("Rules:")
                .contains("Decisions:");
    }

    private static Grammar newSimpleReportGrammar() throws Exception {
        Grammar grammar = new Grammar("""
                parser grammar SimpleReport;

                options {
                    language=Java;
                }

                tokens {
                    A;
                    B;
                }

                start
                    : A
                    | B
                    ;
                """);
        grammar.createLookaheadDFAs(false);
        return grammar;
    }
}
