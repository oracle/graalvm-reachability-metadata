/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xmlunit.XMLUnitException;
import org.xmlunit.builder.Input;
import org.xmlunit.util.Convert;
import org.xmlunit.util.DocumentBuilderFactoryConfigurer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's secure parser configuration subsystem to reject untrusted order documents.
public class SecureOrderParsingTest {
    @Test
    @Timeout(60)
    public void rejectsOrderWithDocumentTypeDeclaration() {
        DocumentBuilderFactory parserFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilderFactoryConfigurer.Default.configure(parserFactory);

        String untrustedOrder = """
                <!DOCTYPE order [<!ENTITY customer "Casey Morgan">]>
                <order><customer>&customer;</customer><total>69.98</total></order>
                """;

        XMLUnitException failure = assertThrows(XMLUnitException.class,
                () -> Convert.toDocument(Input.fromString(untrustedOrder).build(), parserFactory));
        assertTrueMessageContains(failure, "DOCTYPE");
    }

    private static void assertTrueMessageContains(XMLUnitException failure, String expectedText) {
        assertTrue(
                failure.getMessage().contains(expectedText),
                () -> "Unexpected parser failure: " + failure.getMessage());
    }
}
