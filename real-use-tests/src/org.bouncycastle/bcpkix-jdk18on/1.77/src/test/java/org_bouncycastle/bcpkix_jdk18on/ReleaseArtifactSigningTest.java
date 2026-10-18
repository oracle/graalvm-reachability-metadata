/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.Store;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Signs and verifies release artifacts with CMS SignedData from the bcpkix CMS generators.
 *
 * A build pipeline holds a code-signing key and certificate. For every release it writes a
 * detached PKCS#7 signature next to the artifact and an encapsulated signed release manifest.
 * A downstream installer decodes both files, finds the signer certificate in the embedded
 * store, and verifies the signature, the signed attributes, and the artifact bytes it
 * downloaded. A tampered artifact must fail verification.
 */
class ReleaseArtifactSigningTest {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private static final byte[] ARTIFACT = """
            #!/bin/sh
            # acme-agent 4.2.0 installer
            install -m 0755 acme-agent /usr/local/bin/acme-agent
            """.getBytes(StandardCharsets.UTF_8);

    private static final byte[] MANIFEST = """
            {"product": "acme-agent", "version": "4.2.0", "channel": "stable"}
            """.getBytes(StandardCharsets.UTF_8);

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void installerVerifiesDetachedSignatureOverDownloadedArtifact() throws Exception {
        ReleaseSigner signer = new ReleaseSigner(newKeyPair(), now);
        byte[] signatureFile = signer.sign(ARTIFACT, false);

        CMSSignedData detached = new CMSSignedData(new CMSProcessableByteArray(ARTIFACT), signatureFile);
        assertThat(detached.isDetachedSignature()).isTrue();
        assertThat(detached.getSignedContentTypeOID()).isEqualTo(CMSObjectIdentifiers.data.getId());

        SignerInformation signerInfo = singleSigner(detached);
        assertThat(signerInfo.getDigestAlgOID()).isEqualTo(CMSAlgorithm.SHA256.getId());
        X509CertificateHolder signerCertificate = certificateOf(detached, signerInfo);
        assertThat(signerCertificate.getSubject()).isEqualTo(signer.certificate().getSubject());
        assertThat(signerInfo.verify(verifierFor(signerCertificate))).isTrue();

        AttributeTable signedAttributes = signerInfo.getSignedAttributes();
        Attribute digestAttribute = signedAttributes.get(CMSAttributes.messageDigest);
        assertThat(digestAttribute).isNotNull();
        assertThat(signerInfo.getContentDigest())
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(ARTIFACT));
        assertThat(signedAttributes.get(CMSAttributes.signingTime)).isNotNull();
        assertThat(signedAttributes.get(CMSAttributes.contentType).getAttrValues().getObjectAt(0))
                .isEqualTo(CMSObjectIdentifiers.data);

        X509Certificate jcaCertificate = new JcaX509CertificateConverter().getCertificate(signerCertificate);
        assertThat(jcaCertificate.getExtendedKeyUsage()).containsExactly(KeyPurposeId.id_kp_codeSigning.getId());
        assertThat(jcaCertificate.getPublicKey()).isEqualTo(signer.keys().getPublic());

        byte[] tampered = ARTIFACT.clone();
        tampered[tampered.length - 2] = 'X';
        CMSSignedData forged = new CMSSignedData(new CMSProcessableByteArray(tampered), signatureFile);
        SignerInformation forgedSignerInfo = singleSigner(forged);
        assertThatThrownBy(() -> forgedSignerInfo.verify(verifierFor(certificateOf(forged, forgedSignerInfo))))
                .isInstanceOf(CMSException.class)
                .hasMessageContaining("message-digest attribute value does not match calculated value");
    }

    @Test
    void installerReadsEncapsulatedManifestAndRejectsForeignSigner() throws Exception {
        ReleaseSigner signer = new ReleaseSigner(newKeyPair(), now);
        byte[] signedManifest = signer.sign(MANIFEST, true);

        CMSSignedData parsed = new CMSSignedData(signedManifest);
        assertThat(parsed.isDetachedSignature()).isFalse();
        CMSTypedData content = parsed.getSignedContent();
        assertThat(new String((byte[]) content.getContent(), StandardCharsets.UTF_8))
                .contains("\"version\": \"4.2.0\"");

        SignerInformation signerInfo = singleSigner(parsed);
        assertThat(signerInfo.verify(verifierFor(certificateOf(parsed, signerInfo)))).isTrue();
        assertThat(parsed.getCertificates().getMatches(null)).hasSize(1);

        ReleaseSigner impostor = new ReleaseSigner(newKeyPair(), now);
        assertThat(signerInfo.verify(verifierFor(impostor.certificate()))).isFalse();

        CMSSignedData reencoded = new CMSSignedData(parsed.getEncoded());
        assertThat(reencoded.getSignerInfos().size()).isEqualTo(1);
        assertThat(reencoded.getSignedContent().getContentType()).isEqualTo(CMSObjectIdentifiers.data);
    }

    private static SignerInformation singleSigner(CMSSignedData signedData) {
        SignerInformationStore signers = signedData.getSignerInfos();
        assertThat(signers.size()).isEqualTo(1);
        return signers.getSigners().iterator().next();
    }

    @SuppressWarnings("unchecked")
    private static X509CertificateHolder certificateOf(CMSSignedData signedData, SignerInformation signerInfo) {
        Store<X509CertificateHolder> certificates = signedData.getCertificates();
        Collection<X509CertificateHolder> matches = certificates.getMatches(signerInfo.getSID());
        assertThat(matches).hasSize(1);
        return matches.iterator().next();
    }

    private static SignerInformationVerifier verifierFor(X509CertificateHolder certificate) throws Exception {
        return new JcaSimpleSignerInfoVerifierBuilder().build(certificate);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /**
     * The pipeline's code-signing identity: a self-signed certificate and the CMS generator that
     * signs each release payload with it.
     */
    private static final class ReleaseSigner {

        private final KeyPair keys;

        private final X509CertificateHolder certificate;

        ReleaseSigner(KeyPair keys, Instant now) throws Exception {
            this.keys = keys;
            X500Name name = new X500Name("CN=Acme Release Signing, O=Acme Corp, C=US");
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name, BigInteger.valueOf(now.getEpochSecond()), Date.from(now.minus(1, ChronoUnit.HOURS)),
                    Date.from(now.plus(365, ChronoUnit.DAYS)), name, keys.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_codeSigning));
            this.certificate = builder.build(contentSigner());
        }

        KeyPair keys() {
            return keys;
        }

        X509CertificateHolder certificate() {
            return certificate;
        }

        byte[] sign(byte[] payload, boolean encapsulate) throws Exception {
            DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(digests).build(contentSigner(), certificate));
            generator.addCertificates(new JcaCertStore(List.of(certificate)));
            return generator.generate(new CMSProcessableByteArray(payload), encapsulate).getEncoded();
        }

        private ContentSigner contentSigner() throws Exception {
            return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate());
        }
    }
}
