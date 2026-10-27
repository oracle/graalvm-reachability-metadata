/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CRLEntryHolder;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertStore;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Publishes X.509 certificate revocation lists with the bcpkix CRL builders, holders, and converters.
 *
 * A badge-access CA revokes one employee certificate for key compromise and puts a contractor
 * certificate on hold, publishing a signed base CRL with a CRL number and per-entry reason codes.
 * A later delta CRL revokes another certificate and releases the hold. A relying party downloads
 * the DER files, verifies their signatures, applies the delta to the base, and finally lets the JDK
 * PKIX validator reject the compromised certificate while accepting an unrevoked one.
 */
class CertificateRevocationListPublishingTest {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter();

    @Test
    void publishesBaseAndDeltaCrlsThatRelyingPartiesHonour() throws Exception {
        BadgeAccessCa ca = new BadgeAccessCa();
        X509Certificate alice = ca.issue("alice", BigInteger.valueOf(101));
        X509Certificate bob = ca.issue("bob", BigInteger.valueOf(102));
        X509Certificate carol = ca.issue("carol-contractor", BigInteger.valueOf(103));
        X509Certificate dave = ca.issue("dave", BigInteger.valueOf(104));

        Date compromisedAt = Date.from(now.minus(2, ChronoUnit.DAYS));
        byte[] baseDer = ca.publishBase(alice.getSerialNumber(), carol.getSerialNumber(), compromisedAt);
        byte[] deltaDer = ca.publishDelta(bob.getSerialNumber(), carol.getSerialNumber());

        X509CRLHolder base = new X509CRLHolder(baseDer);
        X509CRLHolder delta = new X509CRLHolder(new ByteArrayInputStream(deltaDer));
        ContentVerifierProvider caVerifier = new JcaContentVerifierProviderBuilder().build(ca.certificate);
        assertThat(base.isSignatureValid(caVerifier)).isTrue();
        assertThat(delta.isSignatureValid(caVerifier)).isTrue();
        assertThat(base.getIssuer()).isEqualTo(ca.name);
        assertThat(base.getNextUpdate()).isAfter(base.getThisUpdate());

        assertThat(crlNumber(base)).isEqualTo(BigInteger.ONE);
        assertThat(crlNumber(delta)).isEqualTo(BigInteger.TWO);
        assertThat(base.getExtension(Extension.deltaCRLIndicator)).isNull();
        Extension deltaIndicator = delta.getExtension(Extension.deltaCRLIndicator);
        assertThat(deltaIndicator.isCritical()).isTrue();
        assertThat(CRLNumber.getInstance(deltaIndicator.getParsedValue()).getCRLNumber()).isEqualTo(BigInteger.ONE);
        assertThat(base.getCriticalExtensionOIDs()).isEmpty();
        assertThat(base.getNonCriticalExtensionOIDs())
                .contains(Extension.cRLNumber, Extension.authorityKeyIdentifier);

        X509CRLEntryHolder aliceEntry = base.getRevokedCertificate(alice.getSerialNumber());
        assertThat(aliceEntry.getSerialNumber()).isEqualTo(alice.getSerialNumber());
        assertThat(aliceEntry.getRevocationDate()).isEqualTo(Date.from(now));
        assertThat(reasonOf(aliceEntry)).isEqualTo(CRLReason.keyCompromise);
        ASN1GeneralizedTime invalidity = ASN1GeneralizedTime.getInstance(
                aliceEntry.getExtension(Extension.invalidityDate).getParsedValue());
        assertThat(invalidity.getDate()).isEqualTo(compromisedAt);
        assertThat(reasonOf(base.getRevokedCertificate(carol.getSerialNumber())))
                .isEqualTo(CRLReason.certificateHold);
        assertThat(base.getRevokedCertificate(bob.getSerialNumber())).isNull();

        assertThat(applyDelta(base, delta))
                .containsExactly(alice.getSerialNumber(), bob.getSerialNumber());

        X509CRL downloadedBase = (X509CRL) CertificateFactory.getInstance("X.509")
                .generateCRL(new ByteArrayInputStream(baseDer));
        X509CRL convertedBase = new JcaX509CRLConverter().getCRL(base);
        assertThat(convertedBase).isEqualTo(downloadedBase);
        downloadedBase.verify(ca.certificate.getPublicKey());
        assertThat(downloadedBase.isRevoked(alice)).isTrue();
        assertThat(downloadedBase.isRevoked(carol)).isTrue();
        assertThat(downloadedBase.isRevoked(dave)).isFalse();
        assertThat(downloadedBase.getRevokedCertificate(alice).getRevocationReason().name())
                .isEqualTo("KEY_COMPROMISE");

        PKIXParameters parameters = new PKIXParameters(Set.of(new TrustAnchor(ca.certificate, null)));
        parameters.setRevocationEnabled(true);
        parameters.addCertStore(CertStore.getInstance("Collection",
                new CollectionCertStoreParameters(List.of(downloadedBase))));
        CertPathValidator validator = CertPathValidator.getInstance("PKIX");

        validator.validate(pathOf(dave), parameters);
        assertThatThrownBy(() -> validator.validate(pathOf(alice), parameters))
                .isInstanceOfSatisfying(CertPathValidatorException.class, failure ->
                        assertThat(failure.getReason()).isEqualTo(CertPathValidatorException.BasicReason.REVOKED));
    }

    @Test
    void reissuesFullCrlCarryingForwardEarlierEntries() throws Exception {
        BadgeAccessCa ca = new BadgeAccessCa();
        X509Certificate alice = ca.issue("alice", BigInteger.valueOf(201));
        X509Certificate erin = ca.issue("erin", BigInteger.valueOf(202));
        X509CRLHolder previous = new X509CRLHolder(
                ca.publishBase(alice.getSerialNumber(), BigInteger.valueOf(999), Date.from(now)));

        X509v2CRLBuilder next = new JcaX509v2CRLBuilder(ca.certificate, Date.from(now.plus(1, ChronoUnit.HOURS)));
        next.setNextUpdate(Date.from(now.plus(8, ChronoUnit.DAYS)));
        next.addCRL(previous);
        next.addCRLEntry(erin.getSerialNumber(), Date.from(now.plus(1, ChronoUnit.HOURS)),
                CRLReason.cessationOfOperation);
        next.addExtension(Extension.cRLNumber, false, new CRLNumber(crlNumber(previous).add(BigInteger.ONE)));
        X509CRLHolder reissued = next.build(ca.signer());

        assertThat(reissued.isSignatureValid(new JcaContentVerifierProviderBuilder().build(ca.certificate))).isTrue();
        assertThat(crlNumber(reissued)).isEqualTo(BigInteger.TWO);
        assertThat(serials(reissued.getRevokedCertificates()))
                .containsExactly(alice.getSerialNumber(), erin.getSerialNumber(), BigInteger.valueOf(999));
        assertThat(reasonOf(reissued.getRevokedCertificate(alice.getSerialNumber())))
                .isEqualTo(CRLReason.keyCompromise);
        assertThat(reasonOf(reissued.getRevokedCertificate(erin.getSerialNumber())))
                .isEqualTo(CRLReason.cessationOfOperation);
        assertThat(reissued.getThisUpdate()).isAfter(previous.getThisUpdate());
    }

    private static BigInteger crlNumber(X509CRLHolder crl) {
        return CRLNumber.getInstance(crl.getExtension(Extension.cRLNumber).getParsedValue()).getCRLNumber();
    }

    private static int reasonOf(X509CRLEntryHolder entry) {
        return ASN1Enumerated.getInstance(entry.getExtension(Extension.reasonCode).getParsedValue())
                .intValueExact();
    }

    private static TreeSet<BigInteger> serials(Collection<?> entries) {
        TreeSet<BigInteger> serials = new TreeSet<>();
        for (Object entry : entries) {
            serials.add(((X509CRLEntryHolder) entry).getSerialNumber());
        }
        return serials;
    }

    /// Applies a delta CRL the way RFC 5280 section 5.2.4 describes: entries marked
    /// `removeFromCRL` lift an earlier hold, every other entry adds a revocation.
    private static TreeSet<BigInteger> applyDelta(X509CRLHolder base, X509CRLHolder delta) {
        BigInteger baseNumber = CRLNumber.getInstance(
                delta.getExtension(Extension.deltaCRLIndicator).getParsedValue()).getCRLNumber();
        assertThat(crlNumber(base)).isEqualTo(baseNumber);
        TreeSet<BigInteger> revoked = serials(base.getRevokedCertificates());
        for (Object item : delta.getRevokedCertificates()) {
            X509CRLEntryHolder entry = (X509CRLEntryHolder) item;
            if (reasonOf(entry) == CRLReason.removeFromCRL) {
                revoked.remove(entry.getSerialNumber());
            } else {
                revoked.add(entry.getSerialNumber());
            }
        }
        return revoked;
    }

    private CertPath pathOf(X509Certificate certificate) throws Exception {
        return CertificateFactory.getInstance("X.509").generateCertPath(List.of(certificate));
    }

    /// A small CA that issues badge certificates and publishes the CRLs revoking them.
    private final class BadgeAccessCa {

        private final X500Name name = new X500Name("CN=Badge Access CA, O=Acme Facilities");

        private final KeyPair keys = newKeyPair();

        private final JcaX509ExtensionUtils extensionUtils = new JcaX509ExtensionUtils();

        private final X509Certificate certificate;

        BadgeAccessCa() throws Exception {
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                    Date.from(now.minus(30, ChronoUnit.DAYS)), Date.from(now.plus(3650, ChronoUnit.DAYS)),
                    name, keys.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extensionUtils.createSubjectKeyIdentifier(keys.getPublic()));
            certificate = certificateConverter.getCertificate(builder.build(signer()));
        }

        ContentSigner signer() throws Exception {
            return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate());
        }

        X509Certificate issue(String employee, BigInteger serial) throws Exception {
            KeyPair holderKeys = newKeyPair();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(certificate, serial,
                    Date.from(now.minus(7, ChronoUnit.DAYS)), Date.from(now.plus(365, ChronoUnit.DAYS)),
                    new X500Name("CN=" + employee + ", O=Acme Facilities"), holderKeys.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extensionUtils.createAuthorityKeyIdentifier(certificate));
            X509CertificateHolder holder = builder.build(signer());
            return certificateConverter.getCertificate(holder);
        }

        byte[] publishBase(BigInteger compromised, BigInteger onHold, Date compromisedAt) throws Exception {
            X509v2CRLBuilder builder = new X509v2CRLBuilder(name, Date.from(now));
            builder.setNextUpdate(Date.from(now.plus(7, ChronoUnit.DAYS)));
            builder.addCRLEntry(compromised, Date.from(now), CRLReason.keyCompromise, compromisedAt);
            builder.addCRLEntry(onHold, Date.from(now), CRLReason.certificateHold);
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extensionUtils.createAuthorityKeyIdentifier(certificate));
            builder.addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.ONE));
            return builder.build(signer()).getEncoded();
        }

        byte[] publishDelta(BigInteger superseded, BigInteger released) throws Exception {
            Date issued = Date.from(now.plus(1, ChronoUnit.HOURS));
            X509v2CRLBuilder builder = new JcaX509v2CRLBuilder(certificate, issued);
            builder.setNextUpdate(Date.from(now.plus(1, ChronoUnit.DAYS)));
            builder.addCRLEntry(superseded, issued, CRLReason.superseded);
            ExtensionsGenerator releaseExtensions = new ExtensionsGenerator();
            releaseExtensions.addExtension(Extension.reasonCode, false, CRLReason.lookup(CRLReason.removeFromCRL));
            builder.addCRLEntry(released, issued, releaseExtensions.generate());
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    extensionUtils.createAuthorityKeyIdentifier(certificate));
            builder.addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.TWO));
            builder.addExtension(Extension.deltaCRLIndicator, true, new CRLNumber(BigInteger.ONE));
            return builder.build(signer()).getEncoded();
        }
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
