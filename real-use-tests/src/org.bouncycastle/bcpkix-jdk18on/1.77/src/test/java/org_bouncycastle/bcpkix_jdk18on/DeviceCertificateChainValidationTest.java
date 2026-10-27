/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509ContentVerifierProviderBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.path.CertPath;
import org.bouncycastle.cert.path.CertPathValidation;
import org.bouncycastle.cert.path.CertPathValidationResult;
import org.bouncycastle.cert.path.validations.BasicConstraintsValidation;
import org.bouncycastle.cert.path.validations.CRLValidation;
import org.bouncycastle.cert.path.validations.KeyUsageValidation;
import org.bouncycastle.cert.path.validations.ParentCertIssuedValidation;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.CollectionStore;
import org.bouncycastle.util.Store;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates X.509 certification paths with the bcpkix lightweight cert-path API and its rule set.
 *
 * A mobile-device-management gateway receives client certificate chains from enrolling phones
 * and checks each chain with `CertPath` against parent-signature, basic-constraints, key-usage,
 * and CRL rules. A properly issued chain passes and leaves no critical extension unhandled, while
 * a forged device certificate, a chain through a non-CA certificate, an issuing CA without the
 * key-signing bit, a chain longer than the issuing CA's path-length limit, and a revoked device
 * are each rejected with the failing certificate and rule the gateway logs.
 */
class DeviceCertificateChainValidationTest {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private static final int PARENT_RULE = 0;

    private static final int BASIC_CONSTRAINTS_RULE = 1;

    private static final int KEY_USAGE_RULE = 2;

    private static final int CRL_RULE = 3;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final KeyPair rootKeys = newKeyPair();

    private final X500Name rootName = new X500Name("CN=MDM Root CA,O=Example Fleet");

    private final X509CertificateHolder root = issue(rootName, rootKeys, rootName, rootKeys,
            BigInteger.ONE, new BasicConstraints(true), KeyUsage.keyCertSign | KeyUsage.cRLSign);

    private final KeyPair issuingKeys = newKeyPair();

    private final X500Name issuingName = new X500Name("CN=MDM Device Issuing CA,O=Example Fleet");

    private final X509CertificateHolder issuing = issue(rootName, rootKeys, issuingName, issuingKeys,
            BigInteger.TWO, new BasicConstraints(0), KeyUsage.keyCertSign | KeyUsage.cRLSign);

    @Test
    void acceptsProperlyIssuedDeviceChain() {
        Device phone = enrollDevice("phone-0042", BigInteger.valueOf(4200));
        CertPath chain = new CertPath(new X509CertificateHolder[] {phone.certificate, issuing, root});
        Store<X509CRLHolder> crls = publishedCrls();

        assertThat(chain.length()).isEqualTo(3);
        assertThat(chain.getCertificates()[0].getSubject()).isEqualTo(phone.name);

        CertPathValidationResult signaturesOnly = chain.validate(new CertPathValidation[] {
                new ParentCertIssuedValidation(new JcaX509ContentVerifierProviderBuilder())});
        assertThat(signaturesOnly.isValid()).isFalse();
        assertThat(signaturesOnly.getCause()).hasMessage("Unhandled Critical Extensions");
        assertThat(signaturesOnly.getUnhandledCriticalExtensionOIDs())
                .contains(Extension.basicConstraints, Extension.keyUsage);

        CertPathValidationResult result = chain.validate(gatewayRules(crls));
        assertThat(result.isValid()).isTrue();
        assertThat(result.getCause()).isNull();
        assertThat(result.isDetailed()).isFalse();
        assertThat(result.getUnhandledCriticalExtensionOIDs()).isEmpty();
    }

    @Test
    void rejectsDeviceCertificateForgedWithAnotherKey() {
        KeyPair attackerKeys = newKeyPair();
        Device forged = new Device(new X500Name("CN=phone-6666,O=Example Fleet"), newKeyPair());
        X509CertificateHolder forgedCertificate = issue(issuingName, attackerKeys, forged.name, forged.keys,
                BigInteger.valueOf(6666), null, KeyUsage.digitalSignature);

        CertPathValidationResult result = new CertPath(
                new X509CertificateHolder[] {forgedCertificate, issuing, root})
                .validate(gatewayRules(publishedCrls()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailingCertIndex()).isZero();
        assertThat(result.getFailingRuleIndex()).isEqualTo(PARENT_RULE);
        assertThat(result.getCause()).hasMessage("Certificate signature not for public key in parent");
    }

    @Test
    void rejectsChainIssuedThroughDeviceCertificate() {
        Device phone = enrollDevice("phone-0042", BigInteger.valueOf(4200));
        Device rogue = new Device(new X500Name("CN=rogue-tablet,O=Example Fleet"), newKeyPair());
        X509CertificateHolder rogueCertificate = issue(phone.name, phone.keys, rogue.name, rogue.keys,
                BigInteger.valueOf(9001), null, KeyUsage.digitalSignature);

        CertPathValidationResult result = new CertPath(
                new X509CertificateHolder[] {rogueCertificate, phone.certificate, issuing, root})
                .validate(new CertPathValidation[] {
                        new ParentCertIssuedValidation(new JcaX509ContentVerifierProviderBuilder()),
                        new BasicConstraintsValidation()});

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailingCertIndex()).isZero();
        assertThat(result.getFailingRuleIndex()).isEqualTo(BASIC_CONSTRAINTS_RULE);
        assertThat(result.getCause()).hasMessage("Basic constraints violated: issuer is not a CA");
    }

    @Test
    void rejectsIssuingCaWithoutKeySigningUsage() {
        KeyPair signingOnlyKeys = newKeyPair();
        X500Name signingOnlyName = new X500Name("CN=MDM Misconfigured CA,O=Example Fleet");
        X509CertificateHolder signingOnlyCa = issue(rootName, rootKeys, signingOnlyName, signingOnlyKeys,
                BigInteger.valueOf(3), new BasicConstraints(0), KeyUsage.digitalSignature);
        Device phone = new Device(new X500Name("CN=phone-0043,O=Example Fleet"), newKeyPair());
        X509CertificateHolder phoneCertificate = issue(signingOnlyName, signingOnlyKeys, phone.name,
                phone.keys, BigInteger.valueOf(4300), null, KeyUsage.digitalSignature);

        CertPathValidationResult result = new CertPath(
                new X509CertificateHolder[] {phoneCertificate, signingOnlyCa, root})
                .validate(gatewayRules(publishedCrls()));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailingCertIndex()).isEqualTo(1);
        assertThat(result.getFailingRuleIndex()).isEqualTo(KEY_USAGE_RULE);
        assertThat(result.getCause())
                .hasMessage("Issuer certificate KeyUsage extension does not permit key signing");
    }

    @Test
    void rejectsChainLongerThanIssuingCaPathLength() {
        KeyPair subordinateKeys = newKeyPair();
        X500Name subordinateName = new X500Name("CN=MDM Regional CA,O=Example Fleet");
        X509CertificateHolder subordinate = issue(issuingName, issuingKeys, subordinateName, subordinateKeys,
                BigInteger.valueOf(5), new BasicConstraints(true), KeyUsage.keyCertSign | KeyUsage.cRLSign);
        Device phone = new Device(new X500Name("CN=phone-0044,O=Example Fleet"), newKeyPair());
        X509CertificateHolder phoneCertificate = issue(subordinateName, subordinateKeys, phone.name,
                phone.keys, BigInteger.valueOf(4400), null, KeyUsage.digitalSignature);

        CertPathValidationResult result = new CertPath(
                new X509CertificateHolder[] {phoneCertificate, subordinate, issuing, root})
                .validate(new CertPathValidation[] {
                        new ParentCertIssuedValidation(new JcaX509ContentVerifierProviderBuilder()),
                        new BasicConstraintsValidation()});

        assertThat(result.isValid()).isFalse();
        assertThat(result.getFailingRuleIndex()).isEqualTo(BASIC_CONSTRAINTS_RULE);
        assertThat(result.getCause()).hasMessage("Basic constraints violated: path length exceeded");
    }

    @Test
    void rejectsRevokedDeviceAndReportsEveryFailureWhenEvaluated() {
        Device lost = enrollDevice("phone-0099", BigInteger.valueOf(9900));
        KeyPair attackerKeys = newKeyPair();
        X509CertificateHolder forgedIssuing = issue(rootName, attackerKeys, issuingName, issuingKeys,
                BigInteger.TWO, new BasicConstraints(0), KeyUsage.keyCertSign | KeyUsage.cRLSign);
        Store<X509CRLHolder> crls = publishedCrls(lost.certificate.getSerialNumber());

        CertPathValidationResult revoked = new CertPath(
                new X509CertificateHolder[] {lost.certificate, issuing, root})
                .validate(gatewayRules(crls));
        assertThat(revoked.isValid()).isFalse();
        assertThat(revoked.getFailingCertIndex()).isZero();
        assertThat(revoked.getFailingRuleIndex()).isEqualTo(CRL_RULE);
        assertThat(revoked.getCause()).hasMessage("Certificate revoked");

        CertPathValidationResult evaluated = new CertPath(
                new X509CertificateHolder[] {lost.certificate, forgedIssuing, root})
                .evaluate(gatewayRules(crls));
        assertThat(evaluated.isValid()).isFalse();
        assertThat(evaluated.isDetailed()).isTrue();
        // The parent rule keeps the root as working issuer after rejecting the forged CA.
        assertThat(Arrays.stream(evaluated.getCauses()).map(Throwable::getMessage)).containsExactly(
                "Certificate signature not for public key in parent",
                "Certificate issue does not match parent",
                "Certificate revoked");
        assertThat(evaluated.getFailingRuleIndexes()).containsExactly(PARENT_RULE, PARENT_RULE, CRL_RULE);
        assertThat(evaluated.getFailingCertIndexes()).containsExactly(1, 0, 0);

        CertPathValidationResult missingCrl = new CertPath(
                new X509CertificateHolder[] {lost.certificate, issuing, root})
                .validate(new CertPathValidation[] {
                        new CRLValidation(rootName, new CollectionStore<>(List.of(rootCrl())))});
        assertThat(missingCrl.isValid()).isFalse();
        assertThat(missingCrl.getFailingCertIndex()).isZero();
        assertThat(missingCrl.getCause()).hasMessage("CRL for " + issuingName + " not found");
    }

    private CertPathValidation[] gatewayRules(Store<X509CRLHolder> crls) {
        return new CertPathValidation[] {
                new ParentCertIssuedValidation(new JcaX509ContentVerifierProviderBuilder()),
                new BasicConstraintsValidation(),
                new KeyUsageValidation(),
                new CRLValidation(rootName, crls)};
    }

    private Device enrollDevice(String deviceId, BigInteger serial) {
        Device device = new Device(new X500Name("CN=" + deviceId + ",O=Example Fleet"), newKeyPair());
        device.certificate = issue(issuingName, issuingKeys, device.name, device.keys, serial, null,
                KeyUsage.digitalSignature | KeyUsage.keyAgreement);
        return device;
    }

    private Store<X509CRLHolder> publishedCrls(BigInteger... revokedDevices) {
        X509v2CRLBuilder issuingCrl = new X509v2CRLBuilder(issuingName, Date.from(now));
        issuingCrl.setNextUpdate(Date.from(now.plus(1, ChronoUnit.DAYS)));
        for (BigInteger serial : revokedDevices) {
            issuingCrl.addCRLEntry(serial, Date.from(now), CRLReason.keyCompromise);
        }
        return new CollectionStore<>(List.of(rootCrl(), issuingCrl.build(signer(issuingKeys))));
    }

    private X509CRLHolder rootCrl() {
        X509v2CRLBuilder rootCrl = new X509v2CRLBuilder(rootName, Date.from(now));
        rootCrl.setNextUpdate(Date.from(now.plus(30, ChronoUnit.DAYS)));
        return rootCrl.build(signer(rootKeys));
    }

    private X509CertificateHolder issue(X500Name issuerName, KeyPair issuerKeys, X500Name subjectName,
            KeyPair subjectKeys, BigInteger serial, BasicConstraints basicConstraints, int keyUsage) {
        try {
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, serial,
                    Date.from(now.minus(1, ChronoUnit.HOURS)), Date.from(now.plus(365, ChronoUnit.DAYS)),
                    subjectName, subjectKeys.getPublic());
            if (basicConstraints != null) {
                builder.addExtension(Extension.basicConstraints, true, basicConstraints);
            }
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(keyUsage));
            return builder.build(signer(issuerKeys));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to issue certificate for " + subjectName, e);
        }
    }

    private static ContentSigner signer(KeyPair keys) {
        try {
            return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create signer", e);
        }
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate EC key pair", e);
        }
    }

    /** An enrolling device: its subject name, key pair, and the certificate the issuing CA granted it. */
    private static final class Device {

        private final X500Name name;

        private final KeyPair keys;

        private X509CertificateHolder certificate;

        private Device(X500Name name, KeyPair keys) {
            this.name = name;
            this.keys = keys;
        }
    }
}
