/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds and verifies PKCS#10 certification requests with the bcpkix PKCS#10 builders.
 *
 * A fleet of devices enrolls with a registration authority. Each device generates a key pair
 * and sends a DER-encoded certification request carrying its subject, a one-time challenge
 * password, and an extension request for the names and key usages it needs. The registration
 * authority parses the request, checks proof of possession against the embedded public key,
 * reads the challenge and the requested extensions, and rejects a request whose bytes were
 * altered in transit.
 */
class DeviceEnrollmentRequestTest {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private static final String DEVICE_HOST = "sensor-0042.fleet.acme.test";

    private static final String CHALLENGE = "otp-7Q2K-91XD";

    @Test
    void registrationAuthorityAcceptsWellFormedDeviceRequest() throws Exception {
        KeyPair deviceKeys = newKeyPair();
        byte[] wire = new Device(deviceKeys).enrollmentRequest();

        RegistrationAuthority authority = new RegistrationAuthority();
        Enrollment enrollment = authority.receive(wire);

        assertThat(enrollment.proofOfPossession()).isTrue();
        assertThat(enrollment.commonName()).isEqualTo(DEVICE_HOST);
        assertThat(enrollment.organization()).isEqualTo("Acme Fleet");
        assertThat(enrollment.challenge()).isEqualTo(CHALLENGE);
        assertThat(enrollment.dnsNames()).containsExactly(DEVICE_HOST, "sensor-0042");
        assertThat(enrollment.keyUsage().hasUsages(KeyUsage.digitalSignature)).isTrue();
        assertThat(enrollment.keyUsage().hasUsages(KeyUsage.keyCertSign)).isFalse();
        assertThat(enrollment.extendedKeyUsage().hasKeyPurposeId(KeyPurposeId.id_kp_clientAuth))
                .isTrue();
        assertThat(enrollment.extendedKeyUsage().hasKeyPurposeId(KeyPurposeId.id_kp_serverAuth))
                .isFalse();
        assertThat(enrollment.request().getPublicKey()).isEqualTo(deviceKeys.getPublic());
        assertThat(enrollment.request().getSignatureAlgorithm().getAlgorithm().getId())
                .isEqualTo("1.2.840.10045.4.3.2");
    }

    @Test
    void registrationAuthorityRejectsRequestSignedByAnotherKey() throws Exception {
        KeyPair deviceKeys = newKeyPair();
        KeyPair impostorKeys = newKeyPair();
        PKCS10CertificationRequestBuilder builder = new JcaPKCS10CertificationRequestBuilder(
                Device.subject(), deviceKeys.getPublic());
        byte[] wire = builder.build(signer(impostorKeys)).getEncoded();

        Enrollment enrollment = new RegistrationAuthority().receive(wire);

        assertThat(enrollment.proofOfPossession()).isFalse();
        assertThat(enrollment.challenge()).isNull();
        assertThat(enrollment.request().getAttributes()).isEmpty();
    }

    @Test
    void registrationAuthorityRejectsRequestAlteredInTransit() throws Exception {
        byte[] wire = new Device(newKeyPair()).enrollmentRequest();
        byte[] original = Arrays.copyOf(wire, wire.length);

        byte[] needle = "sensor-0042".getBytes(StandardCharsets.US_ASCII);
        int offset = indexOf(wire, needle);
        assertThat(offset).isNotNegative();
        wire[offset + needle.length - 1] = (byte) '3';

        RegistrationAuthority authority = new RegistrationAuthority();
        Enrollment tampered = authority.receive(wire);
        assertThat(tampered.commonName()).isEqualTo("sensor-0043.fleet.acme.test");
        assertThat(tampered.proofOfPossession()).isFalse();

        assertThat(authority.receive(original).proofOfPossession()).isTrue();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }

    private static ContentSigner signer(KeyPair keys) throws Exception {
        return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate());
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /** A device that requests a client certificate for its own key pair. */
    private record Device(KeyPair keys) {

        static X500Name subject() {
            return new X500NameBuilder(BCStyle.INSTANCE)
                    .addRDN(BCStyle.C, "US")
                    .addRDN(BCStyle.O, "Acme Fleet")
                    .addRDN(BCStyle.OU, "Sensors")
                    .addRDN(BCStyle.CN, DEVICE_HOST)
                    .build();
        }

        byte[] enrollmentRequest() throws Exception {
            ExtensionsGenerator extensions = new ExtensionsGenerator();
            extensions.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName[] {
                            new GeneralName(GeneralName.dNSName, DEVICE_HOST),
                            new GeneralName(GeneralName.dNSName, "sensor-0042")
                    }));
            extensions.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyAgreement));
            extensions.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));

            PKCS10CertificationRequest request =
                    new JcaPKCS10CertificationRequestBuilder(subject(), keys.getPublic())
                            .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_challengePassword,
                                    new DERPrintableString(CHALLENGE))
                            .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest,
                                    extensions.generate())
                            .build(signer(keys));
            return request.getEncoded();
        }
    }

    /** What the registration authority learned from one request. */
    private record Enrollment(JcaPKCS10CertificationRequest request, boolean proofOfPossession,
            String commonName, String organization, String challenge, List<String> dnsNames,
            KeyUsage keyUsage, ExtendedKeyUsage extendedKeyUsage) {
    }

    /** Parses incoming DER requests and checks them before any certificate is issued. */
    private static final class RegistrationAuthority {

        Enrollment receive(byte[] der) throws Exception {
            JcaPKCS10CertificationRequest request = new JcaPKCS10CertificationRequest(der);
            ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder()
                    .build(request.getSubjectPublicKeyInfo());
            boolean proofOfPossession = request.isSignatureValid(verifier);

            X500Name subject = request.getSubject();
            Extensions requested = request.getRequestedExtensions();
            List<String> dnsNames = requested == null ? List.of() : Arrays.stream(
                    GeneralNames.fromExtensions(requested, Extension.subjectAlternativeName)
                            .getNames())
                    .filter(name -> name.getTagNo() == GeneralName.dNSName)
                    .map(name -> ((ASN1String) name.getName()).getString())
                    .toList();
            return new Enrollment(request, proofOfPossession, rdn(subject, BCStyle.CN),
                    rdn(subject, BCStyle.O), challenge(request), dnsNames,
                    requested == null ? null : KeyUsage.fromExtensions(requested),
                    requested == null ? null : ExtendedKeyUsage.fromExtensions(requested));
        }

        private static String challenge(PKCS10CertificationRequest request) {
            Attribute[] attributes =
                    request.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_challengePassword);
            if (attributes.length == 0) {
                return null;
            }
            ASN1Encodable value = attributes[0].getAttributeValues()[0];
            return ((ASN1String) value).getString();
        }

        private static String rdn(X500Name name, ASN1ObjectIdentifier type) {
            return name.getRDNs(type)[0].getFirst().getValue().toString();
        }
    }
}
