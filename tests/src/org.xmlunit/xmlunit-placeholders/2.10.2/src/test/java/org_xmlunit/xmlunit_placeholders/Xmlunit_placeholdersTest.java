/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_placeholders;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.xmlunit.XMLUnitException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Comparison;
import org.xmlunit.diff.ComparisonResult;
import org.xmlunit.diff.ComparisonType;
import org.xmlunit.diff.Diff;
import org.xmlunit.placeholder.IgnorePlaceholderHandler;
import org.xmlunit.placeholder.IsDateTimePlaceholderHandler;
import org.xmlunit.placeholder.IsNumberPlaceholderHandler;
import org.xmlunit.placeholder.MatchesRegexPlaceholderHandler;
import org.xmlunit.placeholder.PlaceholderDifferenceEvaluator;
import org.xmlunit.placeholder.PlaceholderSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Xmlunit_placeholdersTest {
    @Test
    void defaultPlaceholderSupportMatchesTextAttributesNumbersDatesRegexesAndIgnoredValues() {
        String control = """
                <order id="${xmlunit.matchesRegex(^ORD-[0-9]{4}$)}" revision="${xmlunit.isNumber}">
                    <createdAt>${xmlunit.isDateTime}</createdAt>
                    <checksum>${xmlunit.ignore}</checksum>
                    <total>${xmlunit.isNumber}</total>
                    <customerCode>${xmlunit.matchesRegex(^[A-Z]{3}-[0-9]{2}$)}</customerCode>
                </order>
                """;
        String test = """
                <order id="ORD-2026" revision="7">
                    <createdAt>2026-04-30T10:15:30+02:00</createdAt>
                    <checksum>runtime-specific-value</checksum>
                    <total>-1234.50e+2</total>
                    <customerCode>ABC-42</customerCode>
                </order>
                """;

        Diff diff = buildDefaultDiff(control, test);

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void placeholdersCanRepresentMissingTextAndMissingAttributes() {
        String control = """
                <profile id="42" volatileToken="${xmlunit.ignore}">
                    <displayName>${xmlunit.ignore}</displayName>
                    <score>${xmlunit.isNumber}</score>
                </profile>
                """;
        String test = """
                <profile id="42">
                    <displayName/>
                    <score>98.75</score>
                </profile>
                """;

        Diff diff = buildDefaultDiff(control, test);

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void placeholderEvaluatorHandlesTextCdataNodeTypeMismatch() {
        String control = """
                <job>
                    <payload><![CDATA[${xmlunit.matchesRegex(^JOB-[0-9]+$)}]]></payload>
                </job>
                """;
        String test = """
                <job>
                    <payload>JOB-2048</payload>
                </job>
                """;

        Diff diff = buildDefaultDiff(control, test);

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void placeholdersMatchXsiTypeLocalPartsWhenNamespaceUriIsUnchanged() {
        String control = """
                <document xmlns:doc="urn:document-types" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:type="doc:${xmlunit.matchesRegex([A-Z][A-Za-z]+Document)}"/>
                """;
        String test = """
                <document xmlns:doc="urn:document-types" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:type="doc:InvoiceDocument"/>
                """;

        Diff diff = buildDefaultDiff(control, test);

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void placeholdersInXsiTypeDoNotMaskNamespaceUriDifferences() {
        String control = """
                <document xmlns:doc="urn:expected-document-types" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:type="doc:${xmlunit.ignore}"/>
                """;
        String test = """
                <document xmlns:doc="urn:actual-document-types" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:type="doc:InvoiceDocument"/>
                """;

        Diff diff = buildDefaultDiff(control, test);

        assertTrue(diff.hasDifferences());
        assertTrue(diff.fullDescription().contains("urn:actual-document-types"), diff::fullDescription);
    }

    @Test
    void dateTimePlaceholdersAcceptExplicitDateFormatArguments() {
        String control = """
                <audit>
                    <timestamp>${xmlunit.isDateTime(dd/MM/yyyy HH:mm:ss)}</timestamp>
                </audit>
                """;
        String test = """
                <audit>
                    <timestamp>30/04/2026 14:35:21</timestamp>
                </audit>
                """;

        Diff diff = buildDefaultDiff(control, test);

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void customPlaceholderDelimitersAllowNonDefaultMarkerSyntax() {
        String control = """
                <metrics>
                    <count>[[xmlunit.isNumber]]</count>
                    <lastSeen>[[xmlunit.isDateTime]]</lastSeen>
                    <opaque>[[xmlunit.ignore]]</opaque>
                </metrics>
                """;
        String test = """
                <metrics>
                    <count>12345</count>
                    <lastSeen>2026-04-30</lastSeen>
                    <opaque>anything supplied by the application</opaque>
                </metrics>
                """;

        DiffBuilder diffBuilder = DiffBuilder.compare(control).withTest(test).ignoreWhitespace().checkForIdentical();
        Diff diff = PlaceholderSupport.withPlaceholderSupportUsingDelimiters(
                diffBuilder,
                Pattern.quote("[["),
                Pattern.quote("]]"))
                .build();

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void customArgumentDelimitersAndSeparatorAreAppliedToRegexPlaceholders() {
        String control = """
                <events>
                    <event code="[[xmlunit.matchesRegex::^[A-Z]{2}[0-9]{3}$::]]">[[xmlunit.ignore]]</event>
                </events>
                """;
        String test = """
                <events>
                    <event code="AB123">created</event>
                </events>
                """;

        DiffBuilder diffBuilder = DiffBuilder.compare(control).withTest(test).ignoreWhitespace().checkForIdentical();
        Diff diff = PlaceholderSupport.withPlaceholderSupportUsingDelimiters(
                diffBuilder,
                Pattern.quote("[["),
                Pattern.quote("]]"),
                Pattern.quote("::"),
                Pattern.quote("::"),
                Pattern.quote(";;"))
                .build();

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void placeholderSupportCanBeChainedAfterDomainSpecificDifferenceEvaluator() {
        String control = """
                <task id="${xmlunit.matchesRegex(^TASK-[0-9]+$)}" status="pending">
                    <assignee>${xmlunit.ignore}</assignee>
                </task>
                """;
        String test = """
                <task id="TASK-77" status="PENDING">
                    <assignee>Ada Lovelace</assignee>
                </task>
                """;

        DiffBuilder diffBuilder = DiffBuilder.compare(control).withTest(test).ignoreWhitespace().checkForSimilar();
        Diff diff = PlaceholderSupport.withPlaceholderSupportChainedAfter(diffBuilder, this::caseInsensitiveStatus)
                .build();

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    @Test
    void nonMatchingPlaceholderLeavesADifferenceInTheDiffResult() {
        String control = """
                <invoice>
                    <amount>${xmlunit.isNumber}</amount>
                    <reference>${xmlunit.matchesRegex(^INV-[0-9]+$)}</reference>
                </invoice>
                """;
        String test = """
                <invoice>
                    <amount>not-a-number</amount>
                    <reference>INV-100</reference>
                </invoice>
                """;

        Diff diff = buildDefaultDiff(control, test);

        assertTrue(diff.hasDifferences());
        assertTrue(diff.fullDescription().contains("not-a-number"), diff::fullDescription);
    }

    @Test
    void placeholdersMustOccupyTheEntireTextOrAttributeValue() {
        String control = """
                <message>prefix ${xmlunit.ignore}</message>
                """;
        String test = """
                <message>prefix generated-value</message>
                """;

        RuntimeException exception = assertThrows(RuntimeException.class, () -> buildDefaultDiff(control, test));

        assertTrue(exceptionChainContains(exception, "exclusively occupy"), exception::toString);
    }

    @Test
    void directHandlersExposeTheirKeywordsAndComparisonSemantics() {
        IgnorePlaceholderHandler ignoreHandler = new IgnorePlaceholderHandler();
        IsNumberPlaceholderHandler numberHandler = new IsNumberPlaceholderHandler();
        IsDateTimePlaceholderHandler dateTimeHandler = new IsDateTimePlaceholderHandler();
        MatchesRegexPlaceholderHandler regexHandler = new MatchesRegexPlaceholderHandler();

        assertEquals("ignore", ignoreHandler.getKeyword());
        assertEquals(ComparisonResult.EQUAL, ignoreHandler.evaluate("any value"));
        assertEquals("isNumber", numberHandler.getKeyword());
        assertEquals(ComparisonResult.EQUAL, numberHandler.evaluate(" -3.5e+8 "));
        assertEquals(ComparisonResult.DIFFERENT, numberHandler.evaluate("12 apples"));
        assertEquals("isDateTime", dateTimeHandler.getKeyword());
        assertEquals(ComparisonResult.EQUAL, dateTimeHandler.evaluate("30/04/2026", "dd/MM/yyyy"));
        assertEquals(ComparisonResult.DIFFERENT, dateTimeHandler.evaluate("not a date", "dd/MM/yyyy"));
        assertEquals("matchesRegex", regexHandler.getKeyword());
        assertEquals(ComparisonResult.EQUAL, regexHandler.evaluate("ticket-2048", "ticket-[0-9]+"));
        assertEquals(ComparisonResult.DIFFERENT, regexHandler.evaluate("ticket-open", "ticket-[0-9]+"));
    }

    @Test
    void regexHandlerReportsInvalidPatternsAsXmlUnitExceptions() {
        MatchesRegexPlaceholderHandler regexHandler = new MatchesRegexPlaceholderHandler();

        XMLUnitException exception = assertThrows(XMLUnitException.class, () -> regexHandler.evaluate("value", "["));

        assertNotNull(exception.getCause());
    }

    @Test
    void differenceEvaluatorConstructorsUseDefaultDelimitersForBlankArguments() {
        String control = """
                <sample code="${xmlunit.isNumber}">${xmlunit.ignore}</sample>
                """;
        String test = """
                <sample code="123">generated text</sample>
                """;

        Diff diff = DiffBuilder.compare(control)
                .withTest(test)
                .ignoreWhitespace()
                .checkForIdentical()
                .withDifferenceEvaluator(new PlaceholderDifferenceEvaluator(" ", ""))
                .build();

        assertFalse(diff.hasDifferences(), diff::fullDescription);
    }

    private Diff buildDefaultDiff(String control, String test) {
        DiffBuilder diffBuilder = DiffBuilder.compare(control).withTest(test).ignoreWhitespace().checkForIdentical();
        return PlaceholderSupport.withPlaceholderSupport(diffBuilder).build();
    }

    private boolean exceptionChainContains(Throwable throwable, String text) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(text)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ComparisonResult caseInsensitiveStatus(Comparison comparison, ComparisonResult outcome) {
        if (comparison.getType() != ComparisonType.ATTR_VALUE) {
            return outcome;
        }
        Object controlValue = comparison.getControlDetails().getValue();
        Object testValue = comparison.getTestDetails().getValue();
        if ("pending".equals(controlValue) && "PENDING".equals(testValue)) {
            return ComparisonResult.SIMILAR;
        }
        return outcome;
    }
}
