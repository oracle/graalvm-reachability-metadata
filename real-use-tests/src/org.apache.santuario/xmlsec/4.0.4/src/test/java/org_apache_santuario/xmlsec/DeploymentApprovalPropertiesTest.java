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
import org.apache.xml.security.signature.ObjectContainer;
import org.apache.xml.security.signature.Reference;
import org.apache.xml.security.signature.SignatureProperties;
import org.apache.xml.security.signature.SignatureProperty;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises XML Signature signed properties to bind deployment approval provenance to a release configuration.
@Timeout(60)
public class DeploymentApprovalPropertiesTest {
    private static final String NAMESPACE = "urn:example:deployment";
    private static final String PROPERTIES_TYPE = Constants.SignatureSpecNS + "SignatureProperties";

    @Test
    public void deploysOnlyConfigurationWithAuthenticatedApprovalProvenance() throws Exception {
        Init.init();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair releaseManager = generator.generateKeyPair();
        byte[] approved = approveDeployment(releaseManager);

        assertEquals(new Deployment("payments-api", "2025.04", "production", "CHG-704"),
                receiveDeployment(approved, releaseManager.getPublic()));

        Document changedApproval = parse(approved);
        singleElement(changedApproval, NAMESPACE, "Approval").setAttribute("ticket", "CHG-999");
        byte[] relabeledApproval = serialize(changedApproval);
        assertThrows(SecurityException.class,
                () -> receiveDeployment(relabeledApproval, releaseManager.getPublic()),
                "Approval metadata inside a signature Object is not trustworthy unless it is signed");

        Document changedConfiguration = parse(approved);
        singleElement(changedConfiguration, NAMESPACE, "Configuration").setAttribute("revision", "2025.05");
        byte[] substitutedConfiguration = serialize(changedConfiguration);
        assertThrows(SecurityException.class,
                () -> receiveDeployment(substitutedConfiguration, releaseManager.getPublic()),
                "An authentic approval must not authorize a different release configuration");

        Document retargeted = parse(approved);
        singleElement(retargeted, Constants.SignatureSpecNS, "SignatureProperty")
                .setAttribute("Target", "#another-signature");
        byte[] misplacedApproval = serialize(retargeted);
        assertThrows(SecurityException.class,
                () -> receiveDeployment(misplacedApproval, releaseManager.getPublic()),
                "The approval property must describe the signature being verified");
    }

    private static byte[] approveDeployment(KeyPair releaseManager) throws Exception {
        Document document = parse("""
                <d:Delivery xmlns:d="urn:example:deployment">
                    <d:Configuration Id="configuration" service="payments-api" revision="2025.04"/>
                </d:Delivery>
                """.getBytes(StandardCharsets.UTF_8));
        singleElement(document, NAMESPACE, "Configuration").setIdAttributeNS(null, "Id", true);
        XMLSignature signature = new XMLSignature(document, "",
                XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256,
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        signature.setId("approval-signature");
        document.getDocumentElement().appendChild(signature.getElement());

        SignatureProperties properties = new SignatureProperties(document);
        properties.setId("approval-properties");
        SignatureProperty property = new SignatureProperty(document, "#approval-signature");
        Element approval = document.createElementNS(NAMESPACE, "d:Approval");
        approval.setAttribute("environment", "production");
        approval.setAttribute("ticket", "CHG-704");
        property.appendChild(approval);
        properties.addSignatureProperty(property);
        ObjectContainer object = new ObjectContainer(document);
        object.appendChild(properties.getElement());
        signature.appendObject(object);

        signature.addDocument("#configuration", canonicalize(document),
                MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256);
        // SignatureProperties are descriptive, not automatically covered by the signature.
        signature.addDocument("#approval-properties", canonicalize(document),
                MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256, null, PROPERTIES_TYPE);
        signature.sign(releaseManager.getPrivate());
        return serialize(document);
    }

    private static Deployment receiveDeployment(byte[] delivery, PublicKey trustedReleaseManager) throws Exception {
        Document document = parse(delivery);
        Element root = document.getDocumentElement();
        Element configuration = singleElement(document, NAMESPACE, "Configuration");
        Element signatureElement = singleElement(document, Constants.SignatureSpecNS, "Signature");
        Element objectElement = singleElement(document, Constants.SignatureSpecNS, "Object");
        Element propertiesElement = singleElement(document, Constants.SignatureSpecNS, "SignatureProperties");
        Element propertyElement = singleElement(document, Constants.SignatureSpecNS, "SignatureProperty");
        Element approval = singleElement(document, NAMESPACE, "Approval");
        if (!NAMESPACE.equals(root.getNamespaceURI()) || !"Delivery".equals(root.getLocalName())
                || configuration.getParentNode() != root || signatureElement.getParentNode() != root
                || objectElement.getParentNode() != signatureElement
                || propertiesElement.getParentNode() != objectElement
                || propertyElement.getParentNode() != propertiesElement || approval.getParentNode() != propertyElement
                || !"configuration".equals(configuration.getAttribute("Id"))
                || !"approval-signature".equals(signatureElement.getAttribute("Id"))
                || !"approval-properties".equals(propertiesElement.getAttribute("Id"))) {
            throw new SecurityException("Unexpected approved deployment structure");
        }
        configuration.setIdAttributeNS(null, "Id", true);
        propertiesElement.setIdAttributeNS(null, "Id", true);
        SignatureProperties properties = new SignatureProperties(propertiesElement, "");
        if (properties.getLength() != 1 || !"#approval-signature".equals(properties.item(0).getTarget())) {
            throw new SecurityException("Expected one approval property targeting this signature");
        }
        XMLSignature signature = new XMLSignature(signatureElement, "", true);
        if (signature.getSignedInfo().getLength() != 2) {
            throw new SecurityException("Both the configuration and its approval properties must be signed");
        }
        requireReference(signature.getSignedInfo().item(0), "#configuration", "");
        requireReference(signature.getSignedInfo().item(1), "#approval-properties", PROPERTIES_TYPE);
        // The release manager's verification key is provisioned independently of the delivery.
        if (!signature.checkSignatureValue(trustedReleaseManager)) {
            throw new SecurityException("Deployment approval authentication failed");
        }
        if (!"production".equals(approval.getAttribute("environment"))) {
            throw new SecurityException("This deployment service requires production approval");
        }
        return new Deployment(configuration.getAttribute("service"), configuration.getAttribute("revision"),
                approval.getAttribute("environment"), approval.getAttribute("ticket"));
    }

    private static void requireReference(Reference reference, String uri, String type) throws Exception {
        Transforms transforms = reference.getTransforms();
        if (!uri.equals(reference.getURI()) || !type.equals(reference.getType())
                || !MessageDigestAlgorithm.ALGO_ID_DIGEST_SHA256.equals(
                        reference.getMessageDigestAlgorithm().getAlgorithmURI())
                || transforms == null || transforms.getLength() != 1
                || !Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS.equals(transforms.item(0).getURI())) {
            throw new SecurityException("Unexpected deployment signature reference profile");
        }
    }

    private static Transforms canonicalize(Document document) throws Exception {
        Transforms transforms = new Transforms(document);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        return transforms;
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

    private record Deployment(String service, String revision, String environment, String ticket) {
    }
}
