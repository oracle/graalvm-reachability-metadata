/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_santuario.xmlsec;

import org.apache.xml.security.Init;
import org.apache.xml.security.algorithms.MessageDigestAlgorithm;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.keys.KeyInfo;
import org.apache.xml.security.keys.content.X509Data;
import org.apache.xml.security.keys.storage.StorageResolver;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises X509Data issuer-and-serial certificate lookup for signed customs declarations.
@Timeout(60)
public class CustomsCertificateLookupTest {
    private static final String NAMESPACE = "urn:example:customs";

    @Test
    public void authenticatesBrokerUsingALocallyProvisionedCertificate() throws Exception {
        Init.init();
        Identity broker = createIdentity(704);
        byte[] delivery = signDeclaration(broker);
        assertEquals(new Declaration("CUS-704", "BOOK-17", "120"),
                receiveDeclaration(delivery, broker.certificate()));

        Document tampered = parse(delivery);
        singleElement(tampered, NAMESPACE, "Declaration").setAttribute("quantity", "1200");
        byte[] alteredDelivery = serialize(tampered);
        assertThrows(SecurityException.class,
                () -> receiveDeclaration(alteredDelivery, broker.certificate()),
                "Resolving the broker's certificate must not bypass declaration integrity");

        // A matching issuer name is not enough: this serial was never provisioned for the broker.
        Identity unknownSigner = createIdentity(705);
        byte[] unknownDelivery = signDeclaration(unknownSigner);
        assertThrows(SecurityException.class,
                () -> receiveDeclaration(unknownDelivery, broker.certificate()),
                "A valid signature from an unprovisioned certificate must not authorize import");
    }

    private static byte[] signDeclaration(Identity broker) throws Exception {
        Document document = parse("""
                <c:Delivery xmlns:c="urn:example:customs">
                    <c:Declaration Id="declaration" number="CUS-704" sku="BOOK-17" quantity="120"/>
                </c:Delivery>
                """.getBytes(StandardCharsets.UTF_8));
        singleElement(document, NAMESPACE, "Declaration").setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(document, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        document.getDocumentElement().appendChild(signature.getElement());
        Transforms transforms = new Transforms(document);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        signature.addDocument("#declaration", transforms, MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);

        // Send a compact certificate selector, not a certificate or public key supplied by the sender.
        X509Data descriptor = new X509Data(document);
        descriptor.addIssuerSerial(broker.certificate().getIssuerX500Principal().getName(),
                broker.certificate().getSerialNumber());
        signature.getKeyInfo().add(descriptor);
        signature.sign(broker.keys().getPrivate());
        return serialize(document);
    }

    private static Declaration receiveDeclaration(byte[] delivery, X509Certificate trustedBroker) throws Exception {
        Document document = parse(delivery);
        Element root = document.getDocumentElement();
        Element declaration = singleElement(document, NAMESPACE, "Declaration");
        Element signatureElement = singleElement(document, Constants.SignatureSpecNS, "Signature");
        if (!NAMESPACE.equals(root.getNamespaceURI()) || !"Delivery".equals(root.getLocalName())
                || declaration.getParentNode() != root || signatureElement.getParentNode() != root
                || !"declaration".equals(declaration.getAttribute("Id"))) {
            throw new SecurityException("Unexpected customs delivery structure");
        }
        declaration.setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(signatureElement, "", true);
        if (signature.getSignedInfo().getLength() != 1
                || !"#declaration".equals(signature.getSignedInfo().item(0).getURI())) {
            throw new SecurityException("Expected authentication of the declaration being imported");
        }
        Transforms transforms = signature.getSignedInfo().item(0).getTransforms();
        if (transforms == null || transforms.getLength() != 1
                || !Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS.equals(transforms.item(0).getURI())) {
            throw new SecurityException("Expected authentication of the complete declaration");
        }
        KeyInfo keyInfo = signature.getKeyInfo();
        if (keyInfo == null || keyInfo.lengthX509Data() != 1) {
            throw new SecurityException("Missing broker certificate selector");
        }
        X509Data descriptor = keyInfo.itemX509Data(0);
        if (descriptor.lengthIssuerSerial() != 1 || descriptor.lengthCertificate() != 0) {
            throw new SecurityException("Expected an issuer-and-serial selector without an embedded certificate");
        }
        keyInfo.setSecureValidation(true);
        // The store is provisioned out of band. Resolution alone is not certificate trust validation.
        keyInfo.addStorageResolver(new StorageResolver(trustedBroker));
        X509Certificate resolved = keyInfo.getX509Certificate();
        if (!trustedBroker.equals(resolved) || !signature.checkSignatureValue(resolved)) {
            throw new SecurityException("Customs declaration is not authenticated by the trusted broker");
        }
        return new Declaration(declaration.getAttribute("number"), declaration.getAttribute("sku"),
                declaration.getAttribute("quantity"));
    }

    private static Identity createIdentity(int serial) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        X500Name name = new X500Name("CN=Harbor Customs Broker,O=Example Logistics");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(serial),
                Date.from(Instant.parse("2020-01-01T00:00:00Z")),
                Date.from(Instant.parse("2040-01-01T00:00:00Z")), name, keys.getPublic());
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
        return new Identity(keys, certificate);
    }

    private static Element singleElement(Document document, String namespace, String name) {
        NodeList elements = document.getElementsByTagNameNS(namespace, name);
        if (elements.getLength() != 1) {
            throw new SecurityException("Expected exactly one " + name);
        }
        return (Element) elements.item(0);
    }

    private static Document parse(byte[] xml) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(xml)) {
            return XMLUtils.read(input, true);
        }
    }

    private static byte[] serialize(Document document) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XMLUtils.outputDOM(document, output);
            return output.toByteArray();
        }
    }

    private record Identity(KeyPair keys, X509Certificate certificate) {
    }

    private record Declaration(String number, String sku, String quantity) {
    }
}
