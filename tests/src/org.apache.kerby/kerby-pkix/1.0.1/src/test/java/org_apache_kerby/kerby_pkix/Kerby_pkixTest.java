/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_pkix;

import org.apache.kerby.asn1.Asn1;
import org.apache.kerby.asn1.type.Asn1BitString;
import org.apache.kerby.asn1.type.Asn1Boolean;
import org.apache.kerby.asn1.type.Asn1IA5String;
import org.apache.kerby.asn1.type.Asn1Integer;
import org.apache.kerby.asn1.type.Asn1ObjectIdentifier;
import org.apache.kerby.asn1.type.Asn1UtcTime;
import org.apache.kerby.asn1.type.Asn1Utf8String;
import org.apache.kerby.cms.type.ContentInfo;
import org.apache.kerby.cms.type.DigestAlgorithmIdentifier;
import org.apache.kerby.cms.type.DigestAlgorithmIdentifiers;
import org.apache.kerby.cms.type.EncapsulatedContentInfo;
import org.apache.kerby.cms.type.IssuerAndSerialNumber;
import org.apache.kerby.cms.type.SignatureAlgorithmIdentifier;
import org.apache.kerby.cms.type.SignatureValue;
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
import org.apache.kerby.x509.type.AuthorityKeyIdentifier;
import org.apache.kerby.x509.type.BasicConstraints;
import org.apache.kerby.x509.type.Certificate;
import org.apache.kerby.x509.type.CertificateSerialNumber;
import org.apache.kerby.x509.type.DSAParameter;
import org.apache.kerby.x509.type.DhParameter;
import org.apache.kerby.x509.type.DirectoryString;
import org.apache.kerby.x509.type.DisplayText;
import org.apache.kerby.x509.type.Extension;
import org.apache.kerby.x509.type.Extensions;
import org.apache.kerby.x509.type.GeneralName;
import org.apache.kerby.x509.type.GeneralNames;
import org.apache.kerby.x509.type.KeyIdentifier;
import org.apache.kerby.x509.type.KeyUsage;
import org.apache.kerby.x509.type.SubjectPublicKeyInfo;
import org.apache.kerby.x509.type.TBSCertificate;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Kerby_pkixTest {
    private static final String COMMON_NAME_OID = "2.5.4.3";
    private static final String ORGANIZATION_OID = "2.5.4.10";
    private static final String RSA_ENCRYPTION_OID = "1.2.840.113549.1.1.1";
    private static final String SHA256_WITH_RSA_OID = "1.2.840.113549.1.1.11";
    private static final String SHA256_OID = "2.16.840.1.101.3.4.2.1";
    private static final String BASIC_CONSTRAINTS_OID = "2.5.29.19";
    private static final String CMS_DATA_OID = "1.2.840.113549.1.7.1";
    private static final String CMS_SIGNED_DATA_OID = "1.2.840.113549.1.7.2";

    @Test
    void x500NameRoundTripPreservesOrderedRdns() throws Exception {
        Name originalName = createName("client.example.test", "Apache Kerby");

        Name decodedName = new Name();
        decodedName.decode(Asn1.encode(originalName));

        List<RelativeDistinguishedName> rdns = decodedName.getName().getElements();
        assertThat(rdns).hasSize(2);
        assertThat(attributeValue(rdns.get(0).getElements().get(0))).isEqualTo("client.example.test");
        assertThat(attributeType(rdns.get(0).getElements().get(0))).isEqualTo(COMMON_NAME_OID);
        assertThat(attributeValue(rdns.get(1).getElements().get(0))).isEqualTo("Apache Kerby");
        assertThat(attributeType(rdns.get(1).getElements().get(0))).isEqualTo(ORGANIZATION_OID);
    }

    @Test
    void certificateRoundTripPreservesTbsFieldsSubjectPublicKeyAndExtensions() throws Exception {
        Certificate originalCertificate = createCertificate();

        Certificate decodedCertificate = new Certificate();
        decodedCertificate.decode(Asn1.encode(originalCertificate));

        TBSCertificate decodedTbsCertificate = decodedCertificate.getTBSCertificate();
        assertThat(decodedTbsCertificate.getVersion()).isEqualTo(2);
        assertThat(decodedTbsCertificate.getSerialNumber().getValue()).isEqualTo(BigInteger.valueOf(42L));
        assertThat(decodedTbsCertificate.getSignature().getAlgorithm()).isEqualTo(SHA256_WITH_RSA_OID);
        AttributeTypeAndValue issuerCommonName = decodedTbsCertificate.getIssuer().getName()
                .getElements().get(0).getElements().get(0);
        AttributeTypeAndValue subjectCommonName = decodedTbsCertificate.getSubject().getName()
                .getElements().get(0).getElements().get(0);
        assertThat(attributeValue(issuerCommonName)).isEqualTo("issuer.example.test");
        assertThat(attributeValue(subjectCommonName)).isEqualTo("subject.example.test");
        assertThat(decodedTbsCertificate.getSubjectPublicKeyInfo().getAlgorithm().getAlgorithm())
                .isEqualTo(RSA_ENCRYPTION_OID);
        assertThat(decodedTbsCertificate.getSubjectPublicKeyInfo().getSubjectPubKey().getValue())
                .containsExactly(new byte[] {0x30, 0x05, 0x02, 0x01, 0x03});
        assertThat(decodedTbsCertificate.getIssuerUniqueID()).containsExactly(new byte[] {0x01, 0x23});

        Extension decodedExtension = decodedTbsCertificate.getExtensions().getElements().get(0);
        assertThat(decodedExtension.getExtnId().getValue()).isEqualTo(BASIC_CONSTRAINTS_OID);
        assertThat(decodedExtension.getCritical()).isTrue();

        BasicConstraints decodedConstraints = new BasicConstraints();
        decodedConstraints.decode(decodedExtension.getExtnValue());
        assertThat(decodedConstraints.getCA()).isTrue();
        assertThat(decodedConstraints.getPathLenConstraint()).isEqualTo(BigInteger.valueOf(3L));
        assertThat(decodedCertificate.getSignatureAlgorithm().getAlgorithm()).isEqualTo(SHA256_WITH_RSA_OID);
        assertThat(decodedCertificate.getSignature().getValue()).containsExactly(new byte[] {0x55, 0x66, 0x77});
    }

    @Test
    void x509ChoiceAndFlagTypesRoundTripThroughTheirPublicAlternatives() throws Exception {
        GeneralNames originalNames = new GeneralNames();
        GeneralName dnsName = new GeneralName();
        dnsName.setDNSName(new Asn1IA5String("service.example.test"));
        GeneralName uriName = new GeneralName();
        uriName.setUniformResourceIdentifier(new Asn1IA5String("https://service.example.test/metadata"));
        GeneralName ipName = new GeneralName();
        ipName.setIpAddress(new byte[] {127, 0, 0, 1});
        GeneralName registeredIdName = new GeneralName();
        registeredIdName.setRegisteredID(new Asn1ObjectIdentifier("1.3.6.1.5.5.7.3.1"));
        originalNames.add(dnsName);
        originalNames.add(uriName);
        originalNames.add(ipName);
        originalNames.add(registeredIdName);

        GeneralNames decodedNames = new GeneralNames();
        decodedNames.decode(Asn1.encode(originalNames));

        assertThat(decodedNames.getElements()).hasSize(4);
        assertThat(decodedNames.getElements().get(0).getDNSName().getValue()).isEqualTo("service.example.test");
        assertThat(decodedNames.getElements().get(1).getUniformResourceIdentifier().getValue())
                .isEqualTo("https://service.example.test/metadata");
        assertThat(decodedNames.getElements().get(2).getIPAddress()).containsExactly(new byte[] {127, 0, 0, 1});
        assertThat(decodedNames.getElements().get(3).getRegisteredID().getValue()).isEqualTo("1.3.6.1.5.5.7.3.1");

        KeyUsage keyUsage = new KeyUsage();
        keyUsage.setFlags(0x25);
        KeyUsage decodedKeyUsage = new KeyUsage();
        decodedKeyUsage.decode(Asn1.encode(keyUsage));
        assertThat(decodedKeyUsage.getFlags()).isEqualTo(0x25);
        assertThat(decodedKeyUsage.isFlagSet(0x01)).isTrue();
        assertThat(decodedKeyUsage.isFlagSet(0x02)).isFalse();
        assertThat(decodedKeyUsage.isFlagSet(0x04)).isTrue();
        assertThat(decodedKeyUsage.isFlagSet(0x20)).isTrue();

        DirectoryString directoryString = new DirectoryString();
        directoryString.setUtf8String(new Asn1Utf8String("Kerby directory"));
        DirectoryString decodedDirectoryString = new DirectoryString();
        decodedDirectoryString.decode(Asn1.encode(directoryString));
        assertThat(decodedDirectoryString.getUtf8String().getValue()).isEqualTo("Kerby directory");

        DisplayText displayText = new DisplayText();
        displayText.setIA5String(new Asn1IA5String("Kerby display text"));
        DisplayText decodedDisplayText = new DisplayText();
        decodedDisplayText.decode(Asn1.encode(displayText));
        assertThat(decodedDisplayText.getIA5String().getValue()).isEqualTo("Kerby display text");
    }

    @Test
    void authorityKeyIdentifierRoundTripPreservesOptionalIssuerAndSerialNumber() throws Exception {
        KeyIdentifier keyIdentifier = new KeyIdentifier();
        keyIdentifier.setValue(new byte[] {0x10, 0x20, 0x30, 0x40});

        GeneralName issuerDirectoryName = new GeneralName();
        issuerDirectoryName.setDirectoryName(createName("issuer-authority.example.test", "Apache Kerby"));
        GeneralNames issuerNames = new GeneralNames();
        issuerNames.add(issuerDirectoryName);

        CertificateSerialNumber serialNumber = new CertificateSerialNumber();
        serialNumber.setValue(BigInteger.valueOf(9_001L));

        AuthorityKeyIdentifier originalIdentifier = new AuthorityKeyIdentifier();
        originalIdentifier.setKeyIdentifier(keyIdentifier);
        originalIdentifier.setAuthorityCertIssuer(issuerNames);
        originalIdentifier.setAuthorityCertSerialNumber(serialNumber);

        AuthorityKeyIdentifier decodedIdentifier = new AuthorityKeyIdentifier();
        decodedIdentifier.decode(Asn1.encode(originalIdentifier));

        assertThat(decodedIdentifier.getKeyIdentifier().getValue())
                .containsExactly(new byte[] {0x10, 0x20, 0x30, 0x40});
        assertThat(decodedIdentifier.getAuthorityCertSerialNumber().getValue()).isEqualTo(BigInteger.valueOf(9_001L));
        GeneralName decodedIssuerName = decodedIdentifier.getAuthorityCertIssuer().getElements().get(0);
        AttributeTypeAndValue decodedIssuerCommonName = decodedIssuerName.getDirectoryName().getName()
                .getElements().get(0).getElements().get(0);
        assertThat(attributeValue(decodedIssuerCommonName)).isEqualTo("issuer-authority.example.test");
    }

    @Test
    void x509KeyAgreementParameterTypesRoundTripDomainParameters() throws Exception {
        BigInteger dsaP = new BigInteger("1461501637330902918203684832716283019655932542983");
        BigInteger dsaQ = new BigInteger("73075081866545145910184241635814150982796627149");
        BigInteger dsaG = new BigInteger("9988776655443322110099887766554433221100");

        DSAParameter dsaParameter = new DSAParameter();
        dsaParameter.setP(dsaP);
        dsaParameter.setQ(dsaQ);
        dsaParameter.setG(dsaG);

        DSAParameter decodedDsaParameter = new DSAParameter();
        decodedDsaParameter.decode(Asn1.encode(dsaParameter));

        assertThat(decodedDsaParameter.getP()).isEqualTo(dsaP);
        assertThat(decodedDsaParameter.getQ()).isEqualTo(dsaQ);
        assertThat(decodedDsaParameter.getG()).isEqualTo(dsaG);

        BigInteger dhP = new BigInteger("24519928653854221733733552434404946937899825954937634816");
        BigInteger dhG = BigInteger.valueOf(2L);
        BigInteger dhQ = new BigInteger("12259964326927110866866776217202473468949912977468817408");

        DhParameter dhParameter = new DhParameter();
        dhParameter.setP(dhP);
        dhParameter.setG(dhG);
        dhParameter.setQ(dhQ);

        DhParameter decodedDhParameter = new DhParameter();
        decodedDhParameter.decode(Asn1.encode(dhParameter));

        assertThat(decodedDhParameter.getP()).isEqualTo(dhP);
        assertThat(decodedDhParameter.getG()).isEqualTo(dhG);
        assertThat(decodedDhParameter.getQ()).isEqualTo(dhQ);
    }

    @Test
    void cmsSignedDataAndContentInfoRoundTripPreserveAlgorithmsPayloadAndSignerState() throws Exception {
        DigestAlgorithmIdentifier digestAlgorithm = new DigestAlgorithmIdentifier();
        digestAlgorithm.setAlgorithm(SHA256_OID);
        DigestAlgorithmIdentifiers digestAlgorithms = new DigestAlgorithmIdentifiers();
        digestAlgorithms.addElement(digestAlgorithm);

        EncapsulatedContentInfo encapsulatedContentInfo = new EncapsulatedContentInfo();
        encapsulatedContentInfo.setContentType(CMS_DATA_OID);
        encapsulatedContentInfo.setContent(new byte[] {0x01, 0x02, 0x03, 0x04});

        SignerInfo signerInfo = createSignerInfo();
        SignerInfos signerInfos = new SignerInfos();
        signerInfos.addElement(signerInfo);

        SignedData signedData = new SignedData();
        signedData.setVersion(1);
        signedData.setDigestAlgorithms(digestAlgorithms);
        signedData.setEncapContentInfo(encapsulatedContentInfo);
        signedData.setSignerInfos(signerInfos);

        ContentInfo contentInfo = new ContentInfo();
        contentInfo.setContentType(CMS_SIGNED_DATA_OID);
        contentInfo.setContent(signedData);

        ContentInfo decodedContentInfo = new ContentInfo();
        decodedContentInfo.decode(Asn1.encode(contentInfo));
        SignedData decodedSignedData = new SignedData();
        decodedSignedData.decode(Asn1.encode(signedData));

        assertThat(decodedContentInfo.getContentType()).isEqualTo(CMS_SIGNED_DATA_OID);
        assertThat(decodedSignedData.getVersion()).isEqualTo(1);
        assertThat(decodedSignedData.getDigestAlgorithms().getElements()).hasSize(1);
        assertThat(decodedSignedData.getDigestAlgorithms().getElements().get(0).getAlgorithm()).isEqualTo(SHA256_OID);
        assertThat(decodedSignedData.getEncapContentInfo().getContentType()).isEqualTo(CMS_DATA_OID);
        assertThat(decodedSignedData.getEncapContentInfo().getContent())
                .containsExactly(new byte[] {0x01, 0x02, 0x03, 0x04});
        assertThat(decodedSignedData.isSigned()).isTrue();
        assertThat(decodedSignedData.getSignerInfos().getElements()).hasSize(1);
        SignerInfo decodedSignerInfo = decodedSignedData.getSignerInfos().getElements().get(0);
        assertThat(decodedSignerInfo.getCmsVersion()).isEqualTo(1);
        assertThat(decodedSignerInfo.getDigestAlgorithmIdentifier().getAlgorithm()).isEqualTo(SHA256_OID);
        assertThat(decodedSignerInfo.getSignatureAlgorithmIdentifier().getAlgorithm()).isEqualTo(SHA256_WITH_RSA_OID);
        assertThat(decodedSignerInfo.getSignatureValue().getValue()).containsExactly(new byte[] {0x11, 0x22, 0x33});
        IssuerAndSerialNumber decodedIssuerAndSerialNumber = decodedSignerInfo.getSignerIdentifier()
                .getIssuerAndSerialNumber();
        assertThat(decodedIssuerAndSerialNumber.getSerialNumber().getValue()).isEqualTo(BigInteger.valueOf(7L));
        AttributeTypeAndValue signerIssuerCommonName = decodedIssuerAndSerialNumber.getIssuer().getName()
                .getElements().get(0).getElements().get(0);
        assertThat(attributeValue(signerIssuerCommonName)).isEqualTo("signer.example.test");
    }

    @Test
    void pkiUtilityExposesDocumentedStubbedOperations() throws Exception {
        SignedData unsignedData = new SignedData();
        unsignedData.setSignerInfos(new SignerInfos());

        assertThat(PkiUtil.getSignedData(null, null, new byte[] {0x01}, SHA256_WITH_RSA_OID)).isNull();
        assertThat(PkiUtil.validateSignedData(unsignedData)).isFalse();
    }

    private static Certificate createCertificate() throws Exception {
        TBSCertificate tbsCertificate = new TBSCertificate();
        tbsCertificate.setVersion(2);

        CertificateSerialNumber serialNumber = new CertificateSerialNumber();
        serialNumber.setValue(BigInteger.valueOf(42L));
        tbsCertificate.setSerialNumber(serialNumber);
        tbsCertificate.setSignature(createAlgorithmIdentifier(SHA256_WITH_RSA_OID));
        tbsCertificate.setIssuer(createName("issuer.example.test", "Apache Kerby CA"));

        UtcValidityPeriod validityPeriod = new UtcValidityPeriod();
        validityPeriod.setNotBeforeUtcTime(new Asn1UtcTime(new Date(1_704_067_200_000L)));
        validityPeriod.setNotAfterUtcTime(new Asn1UtcTime(new Date(1_735_689_600_000L)));
        tbsCertificate.setValidity(validityPeriod);

        tbsCertificate.setSubject(createName("subject.example.test", "Apache Kerby Client"));
        SubjectPublicKeyInfo publicKeyInfo = new SubjectPublicKeyInfo();
        publicKeyInfo.setAlgorithm(createAlgorithmIdentifier(RSA_ENCRYPTION_OID));
        publicKeyInfo.setSubjectPubKey(new byte[] {0x30, 0x05, 0x02, 0x01, 0x03});
        tbsCertificate.setSubjectPublicKeyInfo(publicKeyInfo);
        tbsCertificate.setIssuerUniqueId(new byte[] {0x01, 0x23});
        tbsCertificate.setExtensions(createExtensions());

        Certificate certificate = new Certificate();
        certificate.setTbsCertificate(tbsCertificate);
        certificate.setSignatureAlgorithm(createAlgorithmIdentifier(SHA256_WITH_RSA_OID));
        certificate.setSignature(new Asn1BitString(new byte[] {0x55, 0x66, 0x77}, 0));
        return certificate;
    }

    private static Extensions createExtensions() throws Exception {
        BasicConstraints basicConstraints = new BasicConstraints();
        basicConstraints.setCA(Asn1Boolean.TRUE);
        basicConstraints.setPathLenConstraint(new Asn1Integer(3));

        Extension extension = new Extension();
        extension.setExtnId(new Asn1ObjectIdentifier(BASIC_CONSTRAINTS_OID));
        extension.setCritical(true);
        extension.setExtnValue(Asn1.encode(basicConstraints));

        Extensions extensions = new Extensions();
        extensions.add(extension);
        return extensions;
    }

    private static SignerInfo createSignerInfo() {
        IssuerAndSerialNumber issuerAndSerialNumber = new IssuerAndSerialNumber();
        issuerAndSerialNumber.setIssuer(createName("signer.example.test", "Apache Kerby"));
        issuerAndSerialNumber.setSerialNumber(7);
        SignerIdentifier signerIdentifier = new SignerIdentifier();
        signerIdentifier.setIssuerAndSerialNumber(issuerAndSerialNumber);

        DigestAlgorithmIdentifier digestAlgorithmIdentifier = new DigestAlgorithmIdentifier();
        digestAlgorithmIdentifier.setAlgorithm(SHA256_OID);
        SignatureAlgorithmIdentifier signatureAlgorithmIdentifier = new SignatureAlgorithmIdentifier();
        signatureAlgorithmIdentifier.setAlgorithm(SHA256_WITH_RSA_OID);
        SignatureValue signatureValue = new SignatureValue();
        signatureValue.setValue(new byte[] {0x11, 0x22, 0x33});

        SignerInfo signerInfo = new SignerInfo();
        signerInfo.setCmsVersion(1);
        signerInfo.setSignerIdentifier(signerIdentifier);
        signerInfo.setDigestAlgorithmIdentifier(digestAlgorithmIdentifier);
        signerInfo.setSignatureAlgorithmIdentifier(signatureAlgorithmIdentifier);
        signerInfo.setSignatureValue(signatureValue);
        return signerInfo;
    }

    private static AlgorithmIdentifier createAlgorithmIdentifier(String algorithmOid) {
        AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier();
        algorithmIdentifier.setAlgorithm(algorithmOid);
        return algorithmIdentifier;
    }

    private static Name createName(String commonName, String organization) {
        RDNSequence rdnSequence = new RDNSequence();
        rdnSequence.add(createRdn(COMMON_NAME_OID, commonName));
        rdnSequence.add(createRdn(ORGANIZATION_OID, organization));

        Name name = new Name();
        name.setName(rdnSequence);
        return name;
    }

    private static RelativeDistinguishedName createRdn(String typeOid, String value) {
        RelativeDistinguishedName rdn = new RelativeDistinguishedName();
        AttributeTypeAndValue attributeTypeAndValue = new AttributeTypeAndValue();
        attributeTypeAndValue.setType(new Asn1ObjectIdentifier(typeOid));
        attributeTypeAndValue.setAttributeValue(new Asn1Utf8String(value));
        rdn.addElement(attributeTypeAndValue);
        return rdn;
    }

    private static String attributeType(AttributeTypeAndValue attributeTypeAndValue) {
        return attributeTypeAndValue.getType().getValue();
    }

    private static String attributeValue(AttributeTypeAndValue attributeTypeAndValue) {
        return attributeTypeAndValue.getAttributeValueAs(Asn1Utf8String.class).getValue();
    }

    private static final class UtcValidityPeriod extends AttCertValidityPeriod {
        void setNotBeforeUtcTime(Asn1UtcTime notBeforeTime) {
            setFieldAs(AttCertValidityPeriodField.NOT_BEFORE, notBeforeTime);
        }

        void setNotAfterUtcTime(Asn1UtcTime notAfterTime) {
            setFieldAs(AttCertValidityPeriodField.NOT_AFTER, notAfterTime);
        }
    }
}
