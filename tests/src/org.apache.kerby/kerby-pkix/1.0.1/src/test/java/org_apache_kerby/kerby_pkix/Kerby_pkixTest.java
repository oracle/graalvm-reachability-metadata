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
import org.apache.kerby.x509.type.AttCertIssuer;
import org.apache.kerby.x509.type.AttCertValidityPeriod;
import org.apache.kerby.x509.type.Attribute;
import org.apache.kerby.x509.type.AttributeCertificate;
import org.apache.kerby.x509.type.AttributeCertificateInfo;
import org.apache.kerby.x509.type.AttributeValues;
import org.apache.kerby.x509.type.Attributes;
import org.apache.kerby.x509.type.BasicConstraints;
import org.apache.kerby.x509.type.Certificate;
import org.apache.kerby.x509.type.CertificateList;
import org.apache.kerby.x509.type.CertificateSerialNumber;
import org.apache.kerby.x509.type.DistributionPoint;
import org.apache.kerby.x509.type.DistributionPointName;
import org.apache.kerby.x509.type.Extension;
import org.apache.kerby.x509.type.Extensions;
import org.apache.kerby.x509.type.GeneralName;
import org.apache.kerby.x509.type.GeneralNames;
import org.apache.kerby.x509.type.Holder;
import org.apache.kerby.x509.type.IssuerSerial;
import org.apache.kerby.x509.type.KeyUsage;
import org.apache.kerby.x509.type.ReasonFlags;
import org.apache.kerby.x509.type.RevokedCertificate;
import org.apache.kerby.x509.type.RevokedCertificates;
import org.apache.kerby.x509.type.RoleSyntax;
import org.apache.kerby.x509.type.SubjectKeyIdentifier;
import org.apache.kerby.x509.type.SubjectPublicKeyInfo;
import org.apache.kerby.x509.type.TBSCertList;
import org.apache.kerby.x509.type.TBSCertificate;
import org.apache.kerby.x509.type.Time;
import org.apache.kerby.x509.type.V2Form;
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
    void modelsCertificateRevocationListsAndDistributionPoints() {
        GeneralName crlLocation = new GeneralName();
        crlLocation.setUniformResourceIdentifier(new Asn1IA5String("https://example.test/crl/root.crl"));
        GeneralNames fullName = new GeneralNames();
        fullName.addElement(crlLocation);

        DistributionPointName distributionPointName = new DistributionPointName();
        distributionPointName.setFullName(fullName);

        ReasonFlags reasons = new ReasonFlags();
        reasons.setFlag(1);
        reasons.setFlag(5);

        GeneralName crlIssuer = new GeneralName();
        crlIssuer.setDirectoryName(name("Kerby CRL Issuer"));
        GeneralNames crlIssuers = new GeneralNames();
        crlIssuers.addElement(crlIssuer);

        DistributionPoint distributionPoint = new DistributionPoint();
        distributionPoint.setDistributionPoint(distributionPointName);
        distributionPoint.setReasons(reasons);
        distributionPoint.setCRLIssuer(crlIssuers);

        CertificateSerialNumber revokedSerialNumber = new CertificateSerialNumber();
        revokedSerialNumber.setValue(BigInteger.valueOf(99L));
        Time revocationDate = generalizedTime(1_701_000_000_000L);

        Extension reasonExtension = new Extension();
        reasonExtension.setExtnId(new Asn1ObjectIdentifier("2.5.29.21"));
        reasonExtension.setCritical(false);
        reasonExtension.setExtnValue(new byte[] {10, 1, 1});
        Extensions entryExtensions = new Extensions();
        entryExtensions.addElement(reasonExtension);

        RevokedCertificate revokedCertificate = new RevokedCertificate();
        revokedCertificate.setUserCertificate(revokedSerialNumber);
        revokedCertificate.setRevocationData(revocationDate);
        revokedCertificate.setCrlEntryExtensions(entryExtensions);
        RevokedCertificates revokedCertificates = new RevokedCertificates();
        revokedCertificates.addElement(revokedCertificate);

        TBSCertList tbsCertList = new TBSCertList();
        tbsCertList.setVersion(new Asn1Integer(BigInteger.ONE));
        tbsCertList.setSignature(algorithm(SHA256_WITH_RSA_OID));
        tbsCertList.setIssuer(name("Kerby CRL Issuer"));
        tbsCertList.setThisUpdata(generalizedTime(1_700_900_000_000L));
        tbsCertList.setNextUpdate(generalizedTime(1_701_500_000_000L));
        tbsCertList.setRevokedCertificates(revokedCertificates);
        tbsCertList.setCrlExtensions(new Extensions());

        CertificateList certificateList = new CertificateList();
        certificateList.setTBSCertList(tbsCertList);
        certificateList.setSignatureAlgorithms(algorithm(SHA256_WITH_RSA_OID));
        certificateList.setSignatureValue(new Asn1BitString(new byte[] {2, 7, 1, 8}, 0));

        assertThat(distributionPoint.getDistributionPoint().getFullName().getElements().get(0)
                .getUniformResourceIdentifier().getValue()).isEqualTo("https://example.test/crl/root.crl");
        assertThat(distributionPoint.getReasons().isFlagSet(1)).isTrue();
        assertThat(distributionPoint.getReasons().isFlagSet(5)).isTrue();
        assertThat(distributionPoint.getReasons().isFlagSet(8)).isFalse();
        assertThat(distributionPoint.getCRLIssuer().getElements().get(0).getDirectoryName().getName().getElements())
                .hasSize(1);
        assertThat(certificateList.getTBSCertList().getVersion().getValue()).isEqualTo(BigInteger.ONE);
        assertThat(certificateList.getTBSCertList().getRevokedCertificates().getElements().get(0)
                .getUserCertificate().getValue()).isEqualTo(BigInteger.valueOf(99L));
        assertThat(certificateList.getTBSCertList().getRevokedCertificates().getElements().get(0)
                .getRevocationDate().generalizedTime()).isEqualTo(new Date(1_701_000_000_000L));
        assertThat(certificateList.getTBSCertList().getRevokedCertificates().getElements().get(0)
                .getCrlEntryExtensions().getElements().get(0).getExtnId().getValue()).isEqualTo("2.5.29.21");
        assertThat(certificateList.getTBSCertList().getNextUpdate().generalizedTime())
                .isEqualTo(new Date(1_701_500_000_000L));
        assertThat(certificateList.getSignatureAlgorithm().getAlgorithm()).isEqualTo(SHA256_WITH_RSA_OID);
        assertThat(certificateList.getSignature().getValue()).isEqualTo(new byte[] {2, 7, 1, 8});
    }

    @Test
    void assemblesAttributeCertificateWithRoleAttribute() {
        IssuerSerial holderCertificate = issuerSerial("Kerby Holder Issuer", 101L);
        Holder holder = new Holder();
        holder.setBaseCertificateId(holderCertificate);
        holder.setEntityName(generalNamesWithDirectoryName("Kerby Attribute Holder"));

        V2Form issuerForm = new V2Form();
        issuerForm.setIssuerName(generalNamesWithDirectoryName("Kerby Attribute Authority"));
        issuerForm.setBaseCertificateId(issuerSerial("Kerby Authority Issuer", 202L));
        AttCertIssuer issuer = new AttCertIssuer();
        issuer.setV2Form(issuerForm);

        GeneralName roleName = new GeneralName();
        roleName.setUniformResourceIdentifier(new Asn1IA5String("urn:kerby:role:administrator"));
        RoleSyntax roleSyntax = new RoleSyntax();
        roleSyntax.setRoleAuthority(generalNamesWithDirectoryName("Kerby Role Authority"));
        roleSyntax.setRoleName(roleName);

        AttributeValues roleValues = new AttributeValues();
        roleValues.addElement(new Asn1Any(roleSyntax));
        Attribute roleAttribute = new Attribute();
        roleAttribute.setAttrType(new Asn1ObjectIdentifier("2.5.24.72"));
        roleAttribute.setAttrValues(roleValues);
        Attributes attributes = new Attributes();
        attributes.addElement(roleAttribute);

        AttributeCertificateInfo certificateInfo = new AttributeCertificateInfo();
        certificateInfo.setVersion(1);
        certificateInfo.setHolder(holder);
        certificateInfo.setIssuer(issuer);
        certificateInfo.setSignature(algorithm(SHA256_WITH_RSA_OID));
        certificateInfo.setSerialNumber(serialNumber(303L));
        certificateInfo.setAttrCertValidityPeriod(validityPeriod(1_700_000_000_000L, 1_700_086_400_000L));
        certificateInfo.setAttributes(attributes);
        certificateInfo.setIssuerUniqueId(new byte[] {4, 3, 2, 1});
        certificateInfo.setExtensions(new Extensions());

        AttributeCertificate certificate = new AttributeCertificate();
        certificate.setAciInfo(certificateInfo);
        certificate.setSignatureAlgorithm(algorithm(SHA256_WITH_RSA_OID));
        certificate.setSignatureValue(new Asn1BitString(new byte[] {8, 6, 7, 5, 3, 0, 9}, 0));

        Attribute storedAttribute = certificate.getAcinfo().getAttributes().getElements().get(0);
        RoleSyntax storedRole = (RoleSyntax) storedAttribute.getAttrValues().getElements().get(0).getValue();

        assertThat(certificate.getAcinfo().getVersion()).isEqualTo(1);
        assertThat(certificate.getAcinfo().getHolder().getBaseCertificateID().getSerial().getValue())
                .isEqualTo(BigInteger.valueOf(101L));
        assertThat(certificate.getAcinfo().getHolder().getEntityName().getElements().get(0)
                .getDirectoryName().getName().getElements()).hasSize(1);
        assertThat(certificate.getAcinfo().getIssuer().getV2Form().getBaseCertificateID().getSerial().getValue())
                .isEqualTo(BigInteger.valueOf(202L));
        assertThat(certificate.getAcinfo().getSerialNumber().getValue()).isEqualTo(BigInteger.valueOf(303L));
        assertThat(certificate.getAcinfo().getAttrCertValidityPeriod().getNotBeforeTime().getValue())
                .isEqualTo(new Date(1_700_000_000_000L));
        assertThat(storedAttribute.getAttrType().getValue()).isEqualTo("2.5.24.72");
        assertThat(storedRole.getRoleAuthority().getElements().get(0).getDirectoryName()).isNotNull();
        assertThat(storedRole.getRoleName().getUniformResourceIdentifier().getValue())
                .isEqualTo("urn:kerby:role:administrator");
        assertThat(certificate.getAcinfo().getIssuerUniqueID()).containsExactly(4, 3, 2, 1);
        assertThat(certificate.getSignatureAlgorithm().getAlgorithm()).isEqualTo(SHA256_WITH_RSA_OID);
        assertThat(certificate.getSignatureValue().getValue()).isEqualTo(new byte[] {8, 6, 7, 5, 3, 0, 9});
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

    private static Time generalizedTime(long timeInMillis) {
        Time time = new Time();
        time.setGeneralTime(new Asn1GeneralizedTime(new Date(timeInMillis)));
        return time;
    }

    private static AttCertValidityPeriod validityPeriod(long notBeforeMillis, long notAfterMillis) {
        AttCertValidityPeriod validityPeriod = new AttCertValidityPeriod();
        validityPeriod.setNotBeforeTime(new Asn1GeneralizedTime(new Date(notBeforeMillis)));
        validityPeriod.setNotAfterTime(new Asn1GeneralizedTime(new Date(notAfterMillis)));
        return validityPeriod;
    }

    private static IssuerSerial issuerSerial(String issuerName, long serialValue) {
        IssuerSerial issuerSerial = new IssuerSerial();
        issuerSerial.setIssuer(generalNamesWithDirectoryName(issuerName));
        issuerSerial.setSerial(serialNumber(serialValue));
        issuerSerial.setIssuerUID(new Asn1BitString(new byte[] {1, 0, 1}, 0));
        return issuerSerial;
    }

    private static CertificateSerialNumber serialNumber(long serialValue) {
        CertificateSerialNumber serialNumber = new CertificateSerialNumber();
        serialNumber.setValue(BigInteger.valueOf(serialValue));
        return serialNumber;
    }

    private static GeneralNames generalNamesWithDirectoryName(String commonNameValue) {
        GeneralName directoryName = new GeneralName();
        directoryName.setDirectoryName(name(commonNameValue));
        GeneralNames generalNames = new GeneralNames();
        generalNames.addElement(directoryName);
        return generalNames;
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
