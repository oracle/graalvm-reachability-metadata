/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Issues an X.509 certificate chain with the bcpkix certificate builders and signers.
 *
 * An internal platform CA creates a self-signed root, delegates to an issuing intermediate
 * with a path-length constraint, and issues a TLS server certificate for a payments service
 * with subject alternative names, key usages, and key identifiers. The application then checks
 * the chain twice: with bcpkix verifiers on the certificate holders, and with the JDK PKIX
 * validator after converting the holders to JCA certificates.
 */
class InternalCaCertificateIssuanceTest {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final JcaX509ExtensionUtils extensionUtils = newExtensionUtils();

    private final JcaX509CertificateConverter converter = new JcaX509CertificateConverter();

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private long nextSerial = 0x1000;

    @Test
    void issuesServerCertificateThatChainsToTheRoot() throws Exception {
        KeyPair rootKeys = newKeyPair();
        X500Name rootName = name("Acme Platform Root CA", "Platform Security");
        X509CertificateHolder root = issueCa(rootName, rootKeys, rootName, rootKeys, null, 1, 3650);

        KeyPair intermediateKeys = newKeyPair();
        X500Name intermediateName = name("Acme Issuing CA 1", "Platform Security");
        X509CertificateHolder intermediate = issueCa(
                intermediateName, intermediateKeys, rootName, rootKeys, root, 0, 1825);

        KeyPair serverKeys = newKeyPair();
        X509CertificateHolder server = issueServer(serverKeys, intermediate, intermediateKeys);

        assertThat(root.getSubject()).isEqualTo(root.getIssuer());
        assertThat(intermediate.getIssuer()).isEqualTo(root.getSubject());
        assertThat(server.getIssuer()).isEqualTo(intermediate.getSubject());
        assertThat(server.getSubject().getRDNs(BCStyle.CN)[0].getFirst().getValue().toString())
                .isEqualTo("payments.internal.acme.test");

        assertThat(root.isSignatureValid(verifierFor(root))).isTrue();
        assertThat(intermediate.isSignatureValid(verifierFor(root))).isTrue();
        assertThat(server.isSignatureValid(verifierFor(intermediate))).isTrue();
        assertThat(server.isSignatureValid(verifierFor(root))).isFalse();
        assertThat(server.isValidOn(Date.from(now.plus(30, ChronoUnit.DAYS)))).isTrue();
        assertThat(server.isValidOn(Date.from(now.plus(400, ChronoUnit.DAYS)))).isFalse();

        BasicConstraints intermediateConstraints =
                BasicConstraints.fromExtensions(intermediate.getExtensions());
        assertThat(intermediateConstraints.isCA()).isTrue();
        assertThat(intermediateConstraints.getPathLenConstraint()).isEqualTo(BigInteger.ZERO);
        assertThat(BasicConstraints.fromExtensions(server.getExtensions()).isCA()).isFalse();

        X509Certificate rootCertificate = converter.getCertificate(root);
        X509Certificate intermediateCertificate = converter.getCertificate(intermediate);
        X509Certificate serverCertificate = converter.getCertificate(server);

        assertThat(serverCertificate.getSubjectAlternativeNames().stream().map(entry -> (String) entry.get(1)))
                .containsExactly("payments.internal.acme.test", "payments", "10.20.30.40");
        assertThat(serverCertificate.getExtendedKeyUsage())
                .containsExactly(KeyPurposeId.id_kp_serverAuth.getId());
        assertThat(serverCertificate.getKeyUsage()[0]).as("digitalSignature").isTrue();
        assertThat(serverCertificate.getKeyUsage()[5]).as("keyCertSign").isFalse();
        assertThat(serverCertificate.getPublicKey()).isEqualTo(serverKeys.getPublic());
        assertThat(AuthorityKeyIdentifier.fromExtensions(server.getExtensions()).getKeyIdentifier())
                .isEqualTo(SubjectKeyIdentifier.fromExtensions(intermediate.getExtensions()).getKeyIdentifier());

        PKIXCertPathValidatorResult result =
                validate(List.of(serverCertificate, intermediateCertificate), rootCertificate);
        assertThat(result.getTrustAnchor().getTrustedCert()).isEqualTo(rootCertificate);
        assertThat(result.getPublicKey()).isEqualTo(serverKeys.getPublic());
    }

    @Test
    void rejectsCertificateIssuedBeyondThePathLengthConstraint() throws Exception {
        KeyPair rootKeys = newKeyPair();
        X500Name rootName = name("Acme Platform Root CA", "Platform Security");
        X509CertificateHolder root = issueCa(rootName, rootKeys, rootName, rootKeys, null, 1, 3650);

        KeyPair intermediateKeys = newKeyPair();
        X500Name intermediateName = name("Acme Issuing CA 1", "Platform Security");
        X509CertificateHolder intermediate = issueCa(
                intermediateName, intermediateKeys, rootName, rootKeys, root, 0, 1825);

        KeyPair rogueKeys = newKeyPair();
        X500Name rogueName = name("Acme Team Sub CA", "Payments");
        X509CertificateHolder rogue = issueCa(
                rogueName, rogueKeys, intermediateName, intermediateKeys, intermediate, 0, 365);

        KeyPair serverKeys = newKeyPair();
        X509CertificateHolder server = issueServer(serverKeys, rogue, rogueKeys);

        assertThat(rogue.isSignatureValid(verifierFor(intermediate))).isTrue();
        assertThat(server.isSignatureValid(verifierFor(rogue))).isTrue();

        List<X509Certificate> path = List.of(
                converter.getCertificate(server),
                converter.getCertificate(rogue),
                converter.getCertificate(intermediate));
        X509Certificate anchor = converter.getCertificate(root);
        assertThatThrownBy(() -> validate(path, anchor))
                .isInstanceOf(CertPathValidatorException.class)
                .hasMessageContaining("pathLenConstraint");
    }

    private X509CertificateHolder issueCa(X500Name subject, KeyPair subjectKeys, X500Name issuer,
            KeyPair issuerKeys, X509CertificateHolder issuerCertificate, int pathLength, int days)
            throws Exception {
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial(), Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(days, ChronoUnit.DAYS)), subject, subjectKeys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(pathLength));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extensionUtils.createSubjectKeyIdentifier(subjectKeys.getPublic()));
        if (issuerCertificate != null) {
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extensionUtils.createAuthorityKeyIdentifier(issuerCertificate));
        }
        return builder.build(signer(issuerKeys));
    }

    private X509CertificateHolder issueServer(KeyPair serverKeys, X509CertificateHolder issuer,
            KeyPair issuerKeys) throws Exception {
        X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "US")
                .addRDN(BCStyle.O, "Acme Corp")
                .addRDN(BCStyle.OU, "Payments")
                .addRDN(BCStyle.CN, "payments.internal.acme.test")
                .build();
        GeneralNames alternativeNames = new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "payments.internal.acme.test"),
                new GeneralName(GeneralName.dNSName, "payments"),
                new GeneralName(GeneralName.iPAddress, "10.20.30.40")
        });
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer.getSubject(), serial(), Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(397, ChronoUnit.DAYS)), subject, serverKeys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        builder.addExtension(Extension.subjectAlternativeName, false, alternativeNames);
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extensionUtils.createSubjectKeyIdentifier(serverKeys.getPublic()));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                extensionUtils.createAuthorityKeyIdentifier(issuer));
        return builder.build(signer(issuerKeys));
    }

    private PKIXCertPathValidatorResult validate(List<X509Certificate> path, X509Certificate root)
            throws Exception {
        CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(path);
        PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
        parameters.setRevocationEnabled(false);
        parameters.setDate(Date.from(now.plus(1, ChronoUnit.DAYS)));
        return (PKIXCertPathValidatorResult) CertPathValidator.getInstance("PKIX")
                .validate(certPath, parameters);
    }

    private static X500Name name(String commonName, String unit) {
        return new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "US")
                .addRDN(BCStyle.O, "Acme Corp")
                .addRDN(BCStyle.OU, unit)
                .addRDN(BCStyle.CN, commonName)
                .build();
    }

    private static ContentSigner signer(KeyPair keys) throws Exception {
        return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate());
    }

    private static ContentVerifierProvider verifierFor(X509CertificateHolder issuer) throws Exception {
        return new JcaContentVerifierProviderBuilder().build(issuer);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private BigInteger serial() {
        return BigInteger.valueOf(nextSerial++);
    }

    private static JcaX509ExtensionUtils newExtensionUtils() {
        try {
            return new JcaX509ExtensionUtils();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
