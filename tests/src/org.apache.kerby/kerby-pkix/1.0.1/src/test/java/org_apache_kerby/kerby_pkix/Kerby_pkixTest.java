/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_pkix;

import org.apache.kerby.asn1.type.Asn1Any;
import org.apache.kerby.asn1.type.Asn1BitString;
import org.apache.kerby.asn1.type.Asn1Boolean;
import org.apache.kerby.asn1.type.Asn1GeneralizedTime;
import org.apache.kerby.asn1.type.Asn1IA5String;
import org.apache.kerby.asn1.type.Asn1Integer;
import org.apache.kerby.asn1.type.Asn1ObjectIdentifier;
import org.apache.kerby.asn1.type.Asn1OctetString;
import org.apache.kerby.asn1.type.Asn1PrintableString;
import org.apache.kerby.cms.type.CertificateChoices;
import org.apache.kerby.cms.type.CertificateSet;
import org.apache.kerby.cms.type.ContentInfo;
import org.apache.kerby.cms.type.DigestAlgorithmIdentifier;
import org.apache.kerby.cms.type.DigestAlgorithmIdentifiers;
import org.apache.kerby.cms.type.EncapsulatedContentInfo;
import org.apache.kerby.cms.type.IssuerAndSerialNumber;
import org.apache.kerby.cms.type.SignatureAlgorithmIdentifier;
import org.apache.kerby.cms.type.SignatureValue;
import org.apache.kerby.cms.type.SignedAttributes;
import org.apache.kerby.cms.type.SignedData;
import org.apache.kerby.cms.type.SignerIdentifier;
import org.apache.kerby.cms.type.SignerInfo;
import org.apache.kerby.cms.type.SignerInfos;
import org.apache.kerby.pkix.PkiUtil;
import org.apache.kerby.x500.type.AttributeTypeAndValue;
import org.apache.kerby.x500.type.Name;
import org.apache.kerby.x500.type.RDNSequence;
import org.apache.kerby.x500.type.RelativeDistinguishedName;
import org.apache.kerby.x509.type.AlgorithmIdentifier;
import org.apache.kerby.x509.type.AttCertValidityPeriod;
import org.apache.kerby.x509.type.BasicConstraints;
import org.apache.kerby.x509.type.Certificate;
import org.apache.kerby.x509.type.CertificateSerialNumber;
import org.apache.kerby.x509.type.Extension;
import org.apache.kerby.x509.type.Extensions;
import org.apache.kerby.x509.type.GeneralName;
import org.apache.kerby.x509.type.GeneralNames;
import org.apache.kerby.x509.type.KeyUsage;
import org.apache.kerby.x509.type.SubjectKeyIdentifier;
import org.apache.kerby.x509.type.SubjectPublicKeyInfo;
import org.apache.kerby.x509.type.TBSCertificate;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

public class Kerby_pkixTest {
    private static final String COMMON_NAME_OID = "2.5.4.3";
    private static final String RSA_ENCRYPTION_OID = "1.2.840.113549.1.1.1";
    private static final String SHA256_WITH_RSA_OID = "1.2.840.113549.1.1.11";
    private static final String DATA_CONTENT_TYPE_OID = "1.2.840.113549.1.7.1";
    private static final byte[] CONTENT = "Kerby PKIX CMS content".getBytes(StandardCharsets.UTF_8);

    @Test
    void buildsX500NameFromRelativeDistinguishedNames() {
        AttributeTypeAndValue commonName = new AttributeTypeAndValue();
        commonName.setType(new Asn1ObjectIdentifier(COMMON_NAME_OID));
        commonName.setAttributeValue(new Asn1PrintableString("Kerby Test CA"));

        RelativeDistinguishedName relativeDistinguishedName = new RelativeDistinguishedName();
        relativeDistinguishedName.addElement(commonName);

        RDNSequence sequence = new RDNSequence();
        sequence.addElement(relativeDistinguishedName);

        Name name = new Name();
        name.setName(sequence);

        AttributeTypeAndValue actualAttribute = name.getName().getElements().get(0).getElements().get(0);
        assertThat(actualAttribute.getType().getValue()).isEqualTo(COMMON_NAME_OID);
        assertThat(name.getName().getElements()).hasSize(1);
        assertThat(relativeDistinguishedName.getElements()).containsExactly(commonName);
    }

    @Test
    void modelsGeneralNamesAndCommonCertificateExtensions() {
        GeneralName dnsName = new GeneralName();
        dnsName.setDNSName(new Asn1IA5String("service.example.test"));

        GeneralName uriName = new GeneralName();
        uriName.setUniformResourceIdentifier(new Asn1IA5String("https://example.test/certs/ca.crt"));

        GeneralName ipAddress = new GeneralName();
        ipAddress.setIpAddress(new byte[] {127, 0, 0, 1});

        GeneralName registeredId = new GeneralName();
        registeredId.setRegisteredID(new Asn1ObjectIdentifier("1.3.6.1.5.5.7.48.1"));

        GeneralNames names = new GeneralNames();
        names.addElement(dnsName);
        names.addElement(uriName);
        names.addElement(ipAddress);
        names.addElement(registeredId);

        BasicConstraints constraints = new BasicConstraints();
        constraints.setCA(new Asn1Boolean(Boolean.TRUE));
        constraints.setPathLenConstraint(new Asn1Integer(BigInteger.valueOf(3L)));

        KeyUsage keyUsage = new KeyUsage();
        keyUsage.setFlag(1);
        keyUsage.setFlag(32);

        assertThat(names.getElements()).hasSize(4);
        assertThat(names.getElements().get(0).getDNSName().getValue()).isEqualTo("service.example.test");
        assertThat(names.getElements().get(1).getUniformResourceIdentifier().getValue())
                .isEqualTo("https://example.test/certs/ca.crt");
        assertThat(names.getElements().get(2).getIPAddress()).containsExactly(127, 0, 0, 1);
        assertThat(names.getElements().get(3).getRegisteredID().getValue()).isEqualTo("1.3.6.1.5.5.7.48.1");
        assertThat(constraints.getCA()).isTrue();
        assertThat(constraints.getPathLenConstraint()).isEqualTo(BigInteger.valueOf(3L));
        assertThat(keyUsage.isFlagSet(1)).isTrue();
        assertThat(keyUsage.isFlagSet(32)).isTrue();
        assertThat(keyUsage.isFlagSet(2)).isFalse();
    }

    @Test
    void assemblesX509CertificateStructure() {
        AlgorithmIdentifier signatureAlgorithm = algorithm(SHA256_WITH_RSA_OID);
        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo();
        publicKeyInfo.setAlgorithm(algorithm(RSA_ENCRYPTION_OID));
        publicKeyInfo.setSubjectPubKey(new byte[] {1, 2, 3, 4, 5});

        CertificateSerialNumber serialNumber = new CertificateSerialNumber();
        serialNumber.setValue(BigInteger.valueOf(42L));

        AttCertValidityPeriod validity = new AttCertValidityPeriod();
        validity.setNotBeforeTime(new Asn1GeneralizedTime(new Date(1_700_000_000_000L)));
        validity.setNotAfterTime(new Asn1GeneralizedTime(new Date(1_703_600_000_000L)));

        Extension subjectAlternativeName = new Extension();
        subjectAlternativeName.setExtnId(new Asn1ObjectIdentifier("2.5.29.17"));
        subjectAlternativeName.setCritical(false);
        subjectAlternativeName.setExtnValue(new byte[] {48, 3, -126, 1, 97});

        Extensions extensions = new Extensions();
        extensions.addElement(subjectAlternativeName);

        TBSCertificate tbsCertificate = new TBSCertificate();
        tbsCertificate.setVersion(2);
        tbsCertificate.setSerialNumber(serialNumber);
        tbsCertificate.setSignature(signatureAlgorithm);
        tbsCertificate.setIssuer(name("Kerby Test Issuer"));
        tbsCertificate.setValidity(validity);
        tbsCertificate.setSubject(name("Kerby Test Subject"));
        tbsCertificate.setSubjectPublicKeyInfo(publicKeyInfo);
        tbsCertificate.setIssuerUniqueId(new byte[] {9, 8, 7});
        tbsCertificate.setSubjectUniqueId(new byte[] {6, 5, 4});
        tbsCertificate.setExtensions(extensions);

        Certificate certificate = new Certificate();
        certificate.setTbsCertificate(tbsCertificate);
        certificate.setSignatureAlgorithm(signatureAlgorithm);
        certificate.setSignature(new Asn1BitString(new byte[] {10, 11, 12}, 0));

        assertThat(certificate.getTBSCertificate().getVersion()).isEqualTo(2);
        assertThat(certificate.getTBSCertificate().getSerialNumber().getValue()).isEqualTo(BigInteger.valueOf(42L));
        assertThat(certificate.getTBSCertificate().getIssuer().getName().getElements()).hasSize(1);
        assertThat(certificate.getTBSCertificate().getSubjectPublicKeyInfo().getAlgorithm().getAlgorithm())
                .isEqualTo(RSA_ENCRYPTION_OID);
        assertThat(certificate.getTBSCertificate().getSubjectPublicKeyInfo().getSubjectPubKey().getValue())
                .isEqualTo(new byte[] {1, 2, 3, 4, 5});
        assertThat(certificate.getTBSCertificate().getIssuerUniqueID()).containsExactly(9, 8, 7);
        assertThat(certificate.getTBSCertificate().getSubject()).isNotNull();
        assertThat(certificate.getTBSCertificate().getExtensions().getElements().get(0).getExtnId().getValue())
                .isEqualTo("2.5.29.17");
        assertThat(certificate.getSignatureAlgorithm().getAlgorithm()).isEqualTo(SHA256_WITH_RSA_OID);
        assertThat(certificate.getSignature().getValue()).isEqualTo(new byte[] {10, 11, 12});
    }

    @Test
    void assemblesCmsSignedDataEnvelope() throws Exception {
        DigestAlgorithmIdentifier digestAlgorithm = new DigestAlgorithmIdentifier();
        digestAlgorithm.setAlgorithm("2.16.840.1.101.3.4.2.1");
        DigestAlgorithmIdentifiers digestAlgorithms = new DigestAlgorithmIdentifiers();
        digestAlgorithms.addElement(digestAlgorithm);

        EncapsulatedContentInfo encapsulatedContentInfo = new EncapsulatedContentInfo();
        encapsulatedContentInfo.setContentType(DATA_CONTENT_TYPE_OID);
        encapsulatedContentInfo.setContent(CONTENT);

        CertificateChoices certificateChoice = new CertificateChoices();
        certificateChoice.setCertificate(minimalCertificate());
        CertificateSet certificateSet = new CertificateSet();
        certificateSet.addElement(certificateChoice);

        IssuerAndSerialNumber issuerAndSerialNumber = new IssuerAndSerialNumber();
        issuerAndSerialNumber.setIssuer(name("Kerby CMS Issuer"));
        issuerAndSerialNumber.setSerialNumber(7);

        SignerIdentifier signerIdentifier = new SignerIdentifier();
        signerIdentifier.setIssuerAndSerialNumber(issuerAndSerialNumber);

        SignatureAlgorithmIdentifier signatureAlgorithm = new SignatureAlgorithmIdentifier();
        signatureAlgorithm.setAlgorithm(SHA256_WITH_RSA_OID);

        SignatureValue signatureValue = new SignatureValue();
        signatureValue.setValue(new byte[] {3, 1, 4, 1, 5});

        SignerInfo signerInfo = new SignerInfo();
        signerInfo.setCmsVersion(1);
        signerInfo.setSignerIdentifier(signerIdentifier);
        signerInfo.setDigestAlgorithmIdentifier(digestAlgorithm);
        signerInfo.setSignedAttributes(new SignedAttributes());
        signerInfo.setSignatureAlgorithmIdentifier(signatureAlgorithm);
        signerInfo.setSignatureValue(signatureValue);

        SignerInfos signerInfos = new SignerInfos();
        signerInfos.addElement(signerInfo);

        SignedData signedData = new SignedData();
        signedData.setVersion(1);
        signedData.setDigestAlgorithms(digestAlgorithms);
        signedData.setEncapContentInfo(encapsulatedContentInfo);
        signedData.setCertificates(certificateSet);
        signedData.setSignerInfos(signerInfos);

        ContentInfo contentInfo = new ContentInfo();
        contentInfo.setContentType("1.2.840.113549.1.7.2");
        contentInfo.setContent(signedData);

        assertThat(signedData.getVersion()).isEqualTo(1);
        assertThat(signedData.getDigestAlgorithms().getElements().get(0).getAlgorithm())
                .isEqualTo("2.16.840.1.101.3.4.2.1");
        assertThat(signedData.getEncapContentInfo().getContent()).isEqualTo(CONTENT);
        assertThat(signedData.getCertificates().getElements().get(0).getCertificate()).isNotNull();
        assertThat(signedData.getSignerInfos().getElements().get(0).getSignerIdentifier()
                .getIssuerAndSerialNumber().getSerialNumber().getValue()).isEqualTo(BigInteger.valueOf(7L));
        assertThat(signedData.isSigned()).isTrue();
        assertThat(contentInfo.getContentType()).isEqualTo("1.2.840.113549.1.7.2");
        assertThat(signedData.getSignerInfos().getElements()).hasSize(1);
        assertThat(PkiUtil.validateSignedData(signedData)).isFalse();
        assertThat(PkiUtil.getSignedData(null, null, CONTENT, SHA256_WITH_RSA_OID)).isNull();
    }

    @Test
    void supportsChoiceAccessorsForDirectoryAndSubjectKeyIdentifiers() {
        GeneralName directoryName = new GeneralName();
        directoryName.setDirectoryName(name("Directory Choice"));

        SubjectKeyIdentifier subjectKeyIdentifier = new SubjectKeyIdentifier();
        subjectKeyIdentifier.setValue(new byte[] {11, 12, 13, 14});

        SignerIdentifier signerIdentifier = new SignerIdentifier();
        signerIdentifier.setSubjectKeyIdentifier(subjectKeyIdentifier);

        Asn1Any anyPrintableString = new Asn1Any(new Asn1PrintableString("typed-any-value"));

        assertThat(directoryName.getDirectoryName().getName().getElements().get(0).getElements().get(0)
                .getType().getValue()).isEqualTo(COMMON_NAME_OID);
        assertThat(signerIdentifier.getSubjectKeyIdentifier().getValue()).isEqualTo(new byte[] {11, 12, 13, 14});
        assertThat(anyPrintableString.getValue()).isInstanceOf(Asn1PrintableString.class);
    }

    private static AlgorithmIdentifier algorithm(String oid) {
        AlgorithmIdentifier identifier = new AlgorithmIdentifier();
        identifier.setAlgorithm(oid);
        identifier.setParameters(new Asn1OctetString(new byte[] {5, 0}));
        return identifier;
    }

    private static Certificate minimalCertificate() {
        TBSCertificate tbsCertificate = new TBSCertificate();
        tbsCertificate.setVersion(2);
        tbsCertificate.setSerialNumber(new CertificateSerialNumber());
        tbsCertificate.getSerialNumber().setValue(BigInteger.ONE);
        tbsCertificate.setSignature(algorithm(SHA256_WITH_RSA_OID));
        tbsCertificate.setIssuer(name("Certificate Set Issuer"));
        tbsCertificate.setSubject(name("Certificate Set Subject"));

        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo();
        publicKeyInfo.setAlgorithm(algorithm(RSA_ENCRYPTION_OID));
        publicKeyInfo.setSubjectPubKey(new byte[] {1, 1, 2, 3, 5, 8});
        tbsCertificate.setSubjectPublicKeyInfo(publicKeyInfo);

        Certificate certificate = new Certificate();
        certificate.setTbsCertificate(tbsCertificate);
        certificate.setSignatureAlgorithm(algorithm(SHA256_WITH_RSA_OID));
        certificate.setSignature(new Asn1BitString(new byte[] {1, 2, 3}, 0));
        return certificate;
    }

    private static Name name(String commonNameValue) {
        AttributeTypeAndValue commonName = new AttributeTypeAndValue();
        commonName.setType(new Asn1ObjectIdentifier(COMMON_NAME_OID));
        commonName.setAttributeValue(new Asn1PrintableString(commonNameValue));

        RelativeDistinguishedName relativeDistinguishedName = new RelativeDistinguishedName();
        relativeDistinguishedName.addElement(commonName);

        RDNSequence sequence = new RDNSequence();
        sequence.addElement(relativeDistinguishedName);

        Name name = new Name();
        name.setName(sequence);
        return name;
    }
}
