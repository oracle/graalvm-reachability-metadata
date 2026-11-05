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
import org.xmlunit.validation.ParsingValidator;
import org.xmlunit.validation.ValidationProblem;
import org.xmlunit.validation.ValidationResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises XMLUnit's DTD parsing validation subsystem to screen carrier shipment messages before dispatch.
public class ShipmentDtdValidationTest {
    @Test
    @Timeout(60)
    public void validatesShipmentMessageAgainstItsEmbeddedCarrierContract() {
        ParsingValidator validator = new ParsingValidator(Languages.XML_DTD_NS_URI);
        validator.setDisableExternalEntities(true);

        ValidationResult validShipment = validator.validateInstance(Input.fromString("""
                <!DOCTYPE shipment [
                    <!ELEMENT shipment (shipmentId, recipient, parcel+)>
                    <!ELEMENT shipmentId (#PCDATA)>
                    <!ELEMENT recipient (#PCDATA)>
                    <!ELEMENT parcel (trackingId, weight)>
                    <!ELEMENT trackingId (#PCDATA)>
                    <!ELEMENT weight (#PCDATA)>
                    <!ATTLIST parcel carrierCode CDATA #REQUIRED>
                ]>
                <shipment>
                    <shipmentId>SHIP-2048</shipmentId>
                    <recipient>Casey Morgan</recipient>
                    <parcel carrierCode="ACME"><trackingId>BOX-1</trackingId><weight>2.5</weight></parcel>
                </shipment>
                """).build());
        ValidationResult invalidShipment = validator.validateInstance(Input.fromString("""
                <!DOCTYPE shipment [
                    <!ELEMENT shipment (shipmentId, recipient, parcel+)>
                    <!ELEMENT shipmentId (#PCDATA)>
                    <!ELEMENT recipient (#PCDATA)>
                    <!ELEMENT parcel (trackingId, weight)>
                    <!ELEMENT trackingId (#PCDATA)>
                    <!ELEMENT weight (#PCDATA)>
                    <!ATTLIST parcel carrierCode CDATA #REQUIRED>
                ]>
                <shipment>
                    <shipmentId>SHIP-2048</shipmentId>
                    <recipient>Casey Morgan</recipient>
                    <parcel><trackingId>BOX-1</trackingId><weight>2.5</weight></parcel>
                </shipment>
                """).build());

        assertTrue(validShipment.isValid(), () -> problemMessages(validShipment.getProblems()));
        assertFalse(invalidShipment.isValid());
        List<ValidationProblem> problems = asList(invalidShipment.getProblems());
        assertFalse(problems.isEmpty());
        assertTrue(problemMessages(problems).contains("carrierCode"));
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
