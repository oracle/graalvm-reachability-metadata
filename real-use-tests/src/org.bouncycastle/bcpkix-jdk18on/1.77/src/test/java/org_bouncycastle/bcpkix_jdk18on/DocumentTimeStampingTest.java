/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TSPValidationException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.bouncycastle.tsp.TimeStampTokenInfo;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Time-stamps documents over RFC 3161 with the bcpkix TSP request, token, and response
 * generators.
 *
 * A contract archive sends the SHA-256 digest of every signed contract to its internal
 * Time-Stamp Authority. The TSA answers with a time-stamp token under its archive policy; the
 * archive validates the response against its request, verifies the token signature with the
 * TSA certificate embedded in the token, and later proves that a stored contract is the one
 * that was stamped. The TSA rejects requests that use a weak digest or an unknown policy.
 */
class DocumentTimeStampingTest {

    private static final ASN1ObjectIdentifier ARCHIVE_POLICY = new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.1.1");

    private static final ASN1ObjectIdentifier UNKNOWN_POLICY = new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.9.9");

    private static final byte[] CONTRACT = """
            SERVICE AGREEMENT 2026-0142
            Supplier: Acme Hosting Ltd.
            Customer: Globex Corporation
            Term: 24 months from the effective date.
            """.getBytes(StandardCharsets.UTF_8);

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void archiveStampsContractAndProvesItsIntegrityLater() throws Exception {
        TimeStampAuthority tsa = new TimeStampAuthority(newKeyPair(), now);
        BigInteger nonce = BigInteger.valueOf(0x5EED_CAFEL);
        TimeStampRequest request = requestFor(sha256(CONTRACT), nonce, ARCHIVE_POLICY);

        byte[] responseBytes = tsa.respond(request.getEncoded(), BigInteger.valueOf(1001), Date.from(now));

        TimeStampResponse response = new TimeStampResponse(responseBytes);
        assertThat(response.getStatus()).isEqualTo(PKIStatus.GRANTED);
        response.validate(request);
        TimeStampToken token = response.getTimeStampToken();
        assertThat(token).isNotNull();

        X509CertificateHolder tsaCertificate = tsaCertificateOf(token);
        assertThat(tsaCertificate.getSubject()).isEqualTo(tsa.certificate().getSubject());
        token.validate(verifierFor(tsaCertificate));
        assertThat(token.isSignatureValid(verifierFor(tsaCertificate))).isTrue();

        TimeStampTokenInfo info = token.getTimeStampInfo();
        assertThat(info.getGenTime()).isEqualTo(Date.from(now));
        assertThat(info.getSerialNumber()).isEqualTo(BigInteger.valueOf(1001));
        assertThat(info.getNonce()).isEqualTo(nonce);
        assertThat(info.getPolicy()).isEqualTo(ARCHIVE_POLICY);
        assertThat(info.getMessageImprintAlgOID()).isEqualTo(TSPAlgorithms.SHA256);
        assertThat(info.getMessageImprintDigest()).isEqualTo(sha256(CONTRACT));
        assertThat(info.isOrdered()).isFalse();
        assertThat(info.getGenTimeAccuracy().getSeconds()).isEqualTo(1);
        assertThat(info.getTsa()).isEqualTo(new GeneralName(tsa.certificate().getSubject()));

        TimeStampToken archived = new TimeStampToken(new TimeStampResponse(responseBytes)
                .getTimeStampToken().toCMSSignedData());
        archived.validate(verifierFor(tsaCertificateOf(archived)));
        assertThat(archived.getTimeStampInfo().getMessageImprintDigest()).isEqualTo(sha256(CONTRACT));

        byte[] amended = new String(CONTRACT, StandardCharsets.UTF_8)
                .replace("24 months", "36 months").getBytes(StandardCharsets.UTF_8);
        assertThat(archived.getTimeStampInfo().getMessageImprintDigest()).isNotEqualTo(sha256(amended));
    }

    @Test
    void archiveRejectsResponseThatDoesNotAnswerItsRequest() throws Exception {
        TimeStampAuthority tsa = new TimeStampAuthority(newKeyPair(), now);
        TimeStampRequest sent = requestFor(sha256(CONTRACT), BigInteger.valueOf(7), ARCHIVE_POLICY);
        TimeStampRequest replayed = requestFor(sha256(CONTRACT), BigInteger.valueOf(8), ARCHIVE_POLICY);

        TimeStampResponse response = new TimeStampResponse(
                tsa.respond(replayed.getEncoded(), BigInteger.valueOf(2002), Date.from(now)));
        assertThat(response.getStatus()).isEqualTo(PKIStatus.GRANTED);

        assertThatThrownBy(() -> response.validate(sent))
                .isInstanceOf(TSPValidationException.class)
                .hasMessageContaining("nonce");

        TimeStampAuthority impostor = new TimeStampAuthority(newKeyPair(), now);
        TimeStampToken token = response.getTimeStampToken();
        assertThat(token.isSignatureValid(verifierFor(impostor.certificate()))).isFalse();
    }

    @Test
    void tsaRejectsWeakDigestsAndUnknownPolicies() throws Exception {
        TimeStampAuthority tsa = new TimeStampAuthority(newKeyPair(), now);

        TimeStampRequest weakDigest = new TimeStampRequestGenerator()
                .generate(TSPAlgorithms.SHA1, MessageDigest.getInstance("SHA-1").digest(CONTRACT));
        TimeStampResponse weakResponse = new TimeStampResponse(
                tsa.respond(weakDigest.getEncoded(), BigInteger.valueOf(3003), Date.from(now)));
        assertThat(weakResponse.getStatus()).isEqualTo(PKIStatus.REJECTION);
        assertThat(weakResponse.getFailInfo().intValue()).isEqualTo(PKIFailureInfo.badAlg);
        assertThat(weakResponse.getTimeStampToken()).isNull();

        TimeStampRequest unknownPolicy = requestFor(sha256(CONTRACT), BigInteger.ONE, UNKNOWN_POLICY);
        TimeStampResponse policyResponse = new TimeStampResponse(
                tsa.respond(unknownPolicy.getEncoded(), BigInteger.valueOf(3004), Date.from(now)));
        assertThat(policyResponse.getStatus()).isEqualTo(PKIStatus.REJECTION);
        assertThat(policyResponse.getFailInfo().intValue() & PKIFailureInfo.unacceptedPolicy)
                .isEqualTo(PKIFailureInfo.unacceptedPolicy);
        assertThat(policyResponse.getTimeStampToken()).isNull();
        assertThat(policyResponse.getStatusString()).isNotBlank();
    }

    private static TimeStampRequest requestFor(byte[] digest, BigInteger nonce, ASN1ObjectIdentifier policy) {
        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true);
        generator.setReqPolicy(policy);
        return generator.generate(TSPAlgorithms.SHA256, digest, nonce);
    }

    @SuppressWarnings("unchecked")
    private static X509CertificateHolder tsaCertificateOf(TimeStampToken token) {
        Collection<X509CertificateHolder> matches = token.getCertificates().getMatches(token.getSID());
        assertThat(matches).hasSize(1);
        return matches.iterator().next();
    }

    private static SignerInformationVerifier verifierFor(X509CertificateHolder certificate) throws Exception {
        return new JcaSimpleSignerInfoVerifierBuilder().build(certificate);
    }

    private static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    /**
     * The archive's Time-Stamp Authority: a certificate restricted to time stamping and the
     * response generator that accepts only SHA-256 imprints under the archive policy.
     */
    private static final class TimeStampAuthority {

        private final X509CertificateHolder certificate;

        private final TimeStampResponseGenerator responder;

        TimeStampAuthority(KeyPair keys, Instant now) throws Exception {
            X500Name name = new X500Name("CN=Acme Archive TSA, O=Acme Corp, C=US");
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    name, BigInteger.valueOf(now.getEpochSecond()), Date.from(now.minus(1, ChronoUnit.HOURS)),
                    Date.from(now.plus(365, ChronoUnit.DAYS)), name, keys.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(Extension.extendedKeyUsage, true,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate());
            this.certificate = builder.build(signer);

            DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();
            DigestCalculator certIdDigest = digests.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
            TimeStampTokenGenerator tokens = new TimeStampTokenGenerator(
                    new JcaSignerInfoGeneratorBuilder(digests).build(signer, certificate),
                    certIdDigest, ARCHIVE_POLICY);
            tokens.addCertificates(new JcaCertStore(List.of(certificate)));
            tokens.setAccuracySeconds(1);
            tokens.setTSA(new GeneralName(name));
            this.responder = new TimeStampResponseGenerator(tokens, Set.of(TSPAlgorithms.SHA256),
                    Set.of(ARCHIVE_POLICY));
        }

        X509CertificateHolder certificate() {
            return certificate;
        }

        byte[] respond(byte[] encodedRequest, BigInteger serialNumber, Date genTime) throws Exception {
            TimeStampRequest request = new TimeStampRequest(encodedRequest);
            return responder.generate(request, serialNumber, genTime).getEncoded();
        }
    }
}
