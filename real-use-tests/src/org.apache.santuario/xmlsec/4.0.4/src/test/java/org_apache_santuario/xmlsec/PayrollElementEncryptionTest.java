/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.xml.security.Init;
import org.apache.xml.security.encryption.XMLCipher;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Exercises DOM XML Encryption to protect payroll bank details while retaining readable routing data.
@Timeout(60)
public class PayrollElementEncryptionTest {
    private static final String PAYROLL_NAMESPACE = "urn:example:payroll";
    private static final String ENCRYPTION_NAMESPACE = "http://www.w3.org/2001/04/xmlenc#";

    @Test
    public void routesEncryptedPayrollAndRestoresBankDetailsAtRecipient() throws Exception {
        Init.init();
        // The payroll service and bank receive this key through their secure provisioning channel.
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(128);
        SecretKey payrollKey = generator.generateKey();

        Document payroll = parse("""
                <p:Payment xmlns:p="urn:example:payroll" batch="PAY-2048">
                    <p:BankRoute>north-branch</p:BankRoute>
                    <p:BankDetails employee="EMP-17">
                        <p:Account>DE02120300000000202051</p:Account>
                        <p:Amount currency="EUR">2850.75</p:Amount>
                    </p:BankDetails>
                </p:Payment>
                """.getBytes(StandardCharsets.UTF_8));
        Element bankDetails = (Element) payroll.getElementsByTagNameNS(PAYROLL_NAMESPACE, "BankDetails").item(0);

        XMLCipher encryptor = XMLCipher.getInstance(XMLCipher.AES_128_GCM);
        encryptor.init(XMLCipher.ENCRYPT_MODE, payrollKey);
        encryptor.doFinal(payroll, bankDetails);

        byte[] transmittedPayroll;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLUtils.outputDOM(payroll, output);
            transmittedPayroll = output.toByteArray();
        }
        String wireXml = new String(transmittedPayroll, StandardCharsets.UTF_8);
        assertFalse(wireXml.contains("DE02120300000000202051"));
        assertFalse(wireXml.contains("2850.75"));
        assertFalse(wireXml.contains("EMP-17"));

        Document received = parse(transmittedPayroll);
        assertEquals("PAY-2048", received.getDocumentElement().getAttribute("batch"));
        assertEquals("north-branch",
                received.getElementsByTagNameNS(PAYROLL_NAMESPACE, "BankRoute").item(0).getTextContent());
        assertEquals(0, received.getElementsByTagNameNS(PAYROLL_NAMESPACE, "BankDetails").getLength());
        assertEquals(1, received.getElementsByTagNameNS(ENCRYPTION_NAMESPACE, "EncryptedData").getLength());

        Element encryptedDetails = (Element) received.getElementsByTagNameNS(
                ENCRYPTION_NAMESPACE, "EncryptedData").item(0);
        XMLCipher decryptor = XMLCipher.getInstance();
        decryptor.setSecureValidation(true);
        decryptor.init(XMLCipher.DECRYPT_MODE, payrollKey);
        decryptor.doFinal(received, encryptedDetails);

        assertEquals(0, received.getElementsByTagNameNS(ENCRYPTION_NAMESPACE, "EncryptedData").getLength());
        assertEquals(1, received.getElementsByTagNameNS(PAYROLL_NAMESPACE, "BankDetails").getLength());
        Element restoredDetails = (Element) received.getElementsByTagNameNS(
                PAYROLL_NAMESPACE, "BankDetails").item(0);
        assertEquals("EMP-17", restoredDetails.getAttribute("employee"));
        assertEquals("DE02120300000000202051",
                restoredDetails.getElementsByTagNameNS(PAYROLL_NAMESPACE, "Account").item(0).getTextContent());
        Element amount = (Element) restoredDetails.getElementsByTagNameNS(PAYROLL_NAMESPACE, "Amount").item(0);
        assertEquals("EUR", amount.getAttribute("currency"));
        assertEquals("2850.75", amount.getTextContent());
        assertEquals("north-branch",
                received.getElementsByTagNameNS(PAYROLL_NAMESPACE, "BankRoute").item(0).getTextContent());
    }

    private static Document parse(byte[] xml) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            return XMLUtils.read(input, true);
        }
    }
}
