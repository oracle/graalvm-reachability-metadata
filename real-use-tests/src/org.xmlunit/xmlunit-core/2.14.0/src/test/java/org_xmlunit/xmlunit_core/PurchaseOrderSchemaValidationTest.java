/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xmlunit.xmlunit_core;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xmlunit.builder.Input;
import org.xmlunit.validation.Languages;
import org.xmlunit.validation.ValidationProblem;
import org.xmlunit.validation.ValidationResult;
import org.xmlunit.validation.Validator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's XML Schema validation subsystem to accept valid purchase orders and reject malformed ones before processing.
public class PurchaseOrderSchemaValidationTest {
    private static final String ORDER_SCHEMA = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="purchaseOrder">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="customerId" type="xs:string"/>
                            <xs:element name="item" maxOccurs="unbounded">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="sku" type="xs:string"/>
                                        <xs:element name="quantity" type="xs:positiveInteger"/>
                                    </xs:sequence>
                                    <xs:attribute name="lineNumber" type="xs:positiveInteger" use="required"/>
                                </xs:complexType>
                            </xs:element>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @Test
    @Timeout(60)
    public void validatesIncomingOrdersAgainstThePublishedContract() {
        Validator validator = Validator.forLanguage(Languages.W3C_XML_SCHEMA_NS_URI);
        validator.setSchemaSource(Input.fromString(ORDER_SCHEMA).build());

        ValidationResult validOrder = validator.validateInstance(Input.fromString("""
                <purchaseOrder>
                    <customerId>customer-42</customerId>
                    <item lineNumber="1"><sku>keyboard</sku><quantity>2</quantity></item>
                </purchaseOrder>
                """).build());
        ValidationResult invalidOrder = validator.validateInstance(Input.fromString("""
                <purchaseOrder>
                    <customerId>customer-42</customerId>
                    <item lineNumber="1"><sku>keyboard</sku><quantity>0</quantity></item>
                </purchaseOrder>
                """).build());

        assertTrue(validOrder.isValid(), () -> problemMessages(validOrder.getProblems()));
        assertFalse(invalidOrder.isValid());
        List<ValidationProblem> problems = asList(invalidOrder.getProblems());
        assertFalse(problems.isEmpty());
        assertTrue(problemMessages(problems).contains("quantity"));
    }

    private static <T> List<T> asList(Iterable<T> values) {
        List<T> result = new ArrayList<>();
        for (T value : values) {
            result.add(value);
        }
        return result;
    }

    private static String problemMessages(Iterable<ValidationProblem> problems) {
        StringBuilder messages = new StringBuilder();
        for (ValidationProblem problem : problems) {
            if (messages.length() > 0) {
                messages.append('\n');
            }
            messages.append(problem.getMessage());
        }
        return messages.toString();
    }
}
