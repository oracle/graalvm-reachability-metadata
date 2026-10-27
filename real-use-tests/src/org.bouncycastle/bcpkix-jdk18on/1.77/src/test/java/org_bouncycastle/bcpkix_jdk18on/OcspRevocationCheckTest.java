/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RespID;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks certificate revocation status over OCSP with the bcpkix OCSP request and response
 * builders.
 *
 * A TLS gateway admits client certificates issued by an internal CA only after asking the CA's
 * OCSP responder about them. The gateway encodes a request with a fresh nonce, the responder
 * decodes it, looks up each serial number in its revocation database, and answers with a
 * response signed by a delegated OCSP-signing certificate. The gateway checks that the
 * responder certificate was issued by the CA for OCSP signing, verifies the signature and the
 * echoed nonce, and reads the good, revoked, and unknown statuses. A garbled request must be
 * answered with a malformed-request status and no response body.
 */
class OcspRevocationCheckTest {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void gatewayReadsGoodRevokedAndUnknownStatusesFromSignedResponse() throws Exception {
        CertificateAuthority ca = new CertificateAuthority(now);
        X509CertificateHolder trusted = ca.issueClient("CN=billing-worker, O=Acme Corp");
        X509CertificateHolder compromised = ca.issueClient("CN=laptop-0042, O=Acme Corp");
        Instant revokedAt = now.minus(2, ChronoUnit.DAYS);
        ca.revoke(compromised.getSerialNumber(), revokedAt, CRLReason.keyCompromise);
        BigInteger foreignSerial = BigInteger.valueOf(0xDEADBEEFL);

        OcspResponder responder = new OcspResponder(ca, now);
        Gateway gateway = new Gateway(ca.certificate());
        byte[] nonce = "gateway-nonce-7f3a".getBytes(StandardCharsets.US_ASCII);
        CertificateID trustedId = gateway.certificateId(trusted.getSerialNumber());
        CertificateID compromisedId = gateway.certificateId(compromised.getSerialNumber());
        CertificateID foreignId = gateway.certificateId(foreignSerial);

        byte[] requestBytes = gateway.request(nonce, trustedId, compromisedId, foreignId);
        OCSPResp response = new OCSPResp(responder.answer(requestBytes));

        assertThat(response.getStatus()).isEqualTo(OCSPRespBuilder.SUCCESSFUL);
        BasicOCSPResp basic = (BasicOCSPResp) response.getResponseObject();
        assertThat(basic.getResponderId()).isEqualTo(new RespID(responder.certificate().getSubject()));
        assertThat(basic.getProducedAt()).isEqualTo(Date.from(now));

        X509CertificateHolder[] embedded = basic.getCerts();
        assertThat(embedded).containsExactly(responder.certificate());
        X509CertificateHolder signerCertificate = embedded[0];
        assertThat(signerCertificate.getIssuer()).isEqualTo(ca.certificate().getSubject());
        assertThat(signerCertificate.isSignatureValid(
                new JcaContentVerifierProviderBuilder().build(ca.certificate()))).isTrue();
        assertThat(ExtendedKeyUsage.fromExtensions(signerCertificate.getExtensions())
                .hasKeyPurposeId(KeyPurposeId.id_kp_OCSPSigning)).isTrue();
        assertThat(signerCertificate.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nocheck)).isNotNull();
        assertThat(basic.isSignatureValid(new JcaContentVerifierProviderBuilder().build(signerCertificate)))
                .isTrue();
        assertThat(basic.isSignatureValid(new JcaContentVerifierProviderBuilder().build(ca.certificate())))
                .isFalse();

        Extension echoedNonce = basic.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
        assertThat(echoedNonce).isNotNull();
        assertThat(echoedNonce.getExtnValue().getOctets()).isEqualTo(nonce);

        SingleResp[] answers = basic.getResponses();
        assertThat(answers).hasSize(3);
        assertThat(answers[0].getCertID()).isEqualTo(trustedId);
        assertThat(answers[0].getCertStatus()).isEqualTo(CertificateStatus.GOOD);
        assertThat(answers[0].getThisUpdate()).isEqualTo(Date.from(now));
        assertThat(answers[0].getNextUpdate()).isEqualTo(Date.from(now.plus(1, ChronoUnit.HOURS)));

        assertThat(answers[1].getCertID()).isEqualTo(compromisedId);
        assertThat(answers[1].getCertStatus()).isInstanceOf(RevokedStatus.class);
        RevokedStatus revoked = (RevokedStatus) answers[1].getCertStatus();
        assertThat(revoked.getRevocationTime()).isEqualTo(Date.from(revokedAt));
        assertThat(revoked.hasRevocationReason()).isTrue();
        assertThat(revoked.getRevocationReason()).isEqualTo(CRLReason.keyCompromise);

        assertThat(answers[2].getCertID()).isEqualTo(foreignId);
        assertThat(answers[2].getCertStatus()).isInstanceOf(UnknownStatus.class);
        assertThat(answers[2].getCertID().matchesIssuer(ca.certificate(), gateway.digests())).isTrue();
    }

    @Test
    void responderRejectsGarbledRequestAndGatewayParsesRequestRoundTrip() throws Exception {
        CertificateAuthority ca = new CertificateAuthority(now);
        OcspResponder responder = new OcspResponder(ca, now);
        Gateway gateway = new Gateway(ca.certificate());

        byte[] garbled = "GET /ocsp HTTP/1.1".getBytes(StandardCharsets.US_ASCII);
        OCSPResp rejection = new OCSPResp(responder.answer(garbled));
        assertThat(rejection.getStatus()).isEqualTo(OCSPRespBuilder.MALFORMED_REQUEST);
        assertThat(rejection.getResponseObject()).isNull();

        X509CertificateHolder client = ca.issueClient("CN=build-agent, O=Acme Corp");
        CertificateID id = gateway.certificateId(client.getSerialNumber());
        byte[] nonce = "round-trip-nonce".getBytes(StandardCharsets.US_ASCII);
        OCSPReq decoded = new OCSPReq(gateway.request(nonce, id));
        assertThat(decoded.isSigned()).isFalse();
        assertThat(decoded.getVersionNumber()).isEqualTo(1);
        Req[] entries = decoded.getRequestList();
        assertThat(entries).hasSize(1);
        assertThat(entries[0].getCertID()).isEqualTo(id);
        assertThat(entries[0].getCertID().getSerialNumber()).isEqualTo(client.getSerialNumber());
        assertThat(entries[0].getCertID().getHashAlgOID()).isEqualTo(CertificateID.HASH_SHA1.getAlgorithm());
        assertThat(decoded.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce).getExtnValue().getOctets())
                .isEqualTo(nonce);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static ContentSigner signer(KeyPair keys) throws Exception {
        return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate());
    }

    /** A revocation entry in the CA database. */
    private record Revocation(Instant time, int reason) {
    }

    /**
     * The internal CA: a self-signed root that issues client certificates and the delegated OCSP
     * signing certificate, and keeps the revocation database.
     */
    private static final class CertificateAuthority {

        private final Instant now;

        private final KeyPair keys = newKeyPair();

        private final X500Name name = new X500Name("CN=Acme Client CA, O=Acme Corp, C=US");

        private final X509CertificateHolder certificate;

        private final Map<BigInteger, Revocation> revocations = new HashMap<>();

        private long nextSerial = 0x2000;

        CertificateAuthority(Instant now) throws Exception {
            this.now = now;
            JcaX509v3CertificateBuilder builder = builder(name, keys);
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            this.certificate = builder.build(signer(keys));
        }

        X509CertificateHolder certificate() {
            return certificate;
        }

        X509CertificateHolder issueClient(String subject) throws Exception {
            JcaX509v3CertificateBuilder builder = builder(new X500Name(subject), newKeyPair());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
            return builder.build(signer(keys));
        }

        X509CertificateHolder issueOcspSigner(String subject, KeyPair responderKeys) throws Exception {
            JcaX509v3CertificateBuilder builder = builder(new X500Name(subject), responderKeys);
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(Extension.extendedKeyUsage, true,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_OCSPSigning));
            builder.addExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nocheck, false, DERNull.INSTANCE);
            return builder.build(signer(keys));
        }

        void revoke(BigInteger serial, Instant time, int reason) {
            revocations.put(serial, new Revocation(time, reason));
        }

        boolean issued(BigInteger serial) {
            return serial.longValue() >= 0x2000 && serial.longValue() < nextSerial;
        }

        Revocation revocationOf(BigInteger serial) {
            return revocations.get(serial);
        }

        private JcaX509v3CertificateBuilder builder(X500Name subject, KeyPair subjectKeys) {
            return new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(nextSerial++),
                    Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(365, ChronoUnit.DAYS)),
                    subject, subjectKeys.getPublic());
        }
    }

    /** The CA's OCSP endpoint, signing answers with its delegated OCSP-signing certificate. */
    private static final class OcspResponder {

        private final CertificateAuthority ca;

        private final Instant now;

        private final KeyPair keys = newKeyPair();

        private final X509CertificateHolder certificate;

        private final DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();

        OcspResponder(CertificateAuthority ca, Instant now) throws Exception {
            this.ca = ca;
            this.now = now;
            this.certificate = ca.issueOcspSigner("CN=Acme OCSP Responder, O=Acme Corp", keys);
        }

        X509CertificateHolder certificate() {
            return certificate;
        }

        byte[] answer(byte[] requestBytes) throws Exception {
            OCSPReq request;
            try {
                request = new OCSPReq(requestBytes);
            } catch (IOException malformed) {
                return new OCSPRespBuilder().build(OCSPRespBuilder.MALFORMED_REQUEST, null).getEncoded();
            }
            BasicOCSPRespBuilder builder = new BasicOCSPRespBuilder(new RespID(certificate.getSubject()));
            for (Req entry : request.getRequestList()) {
                CertificateID id = entry.getCertID();
                builder.addResponse(id, statusOf(id), Date.from(now), Date.from(now.plus(1, ChronoUnit.HOURS)),
                        null);
            }
            Extension nonce = request.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
            if (nonce != null) {
                builder.setResponseExtensions(new Extensions(nonce));
            }
            BasicOCSPResp basic = builder.build(signer(keys), new X509CertificateHolder[] {certificate},
                    Date.from(now));
            return new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic).getEncoded();
        }

        private CertificateStatus statusOf(CertificateID id) throws Exception {
            if (!id.matchesIssuer(ca.certificate(), digests) || !ca.issued(id.getSerialNumber())) {
                return new UnknownStatus();
            }
            Revocation revocation = ca.revocationOf(id.getSerialNumber());
            if (revocation == null) {
                return CertificateStatus.GOOD;
            }
            return new RevokedStatus(Date.from(revocation.time()), revocation.reason());
        }
    }

    /** The TLS gateway that asks the responder about client certificates issued by the CA. */
    private static final class Gateway {

        private final X509CertificateHolder issuer;

        private final DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();

        Gateway(X509CertificateHolder issuer) throws Exception {
            this.issuer = issuer;
        }

        DigestCalculatorProvider digests() {
            return digests;
        }

        CertificateID certificateId(BigInteger serial) throws Exception {
            DigestCalculator sha1 = digests.get(CertificateID.HASH_SHA1);
            return new CertificateID(sha1, issuer, serial);
        }

        byte[] request(byte[] nonce, CertificateID... ids) throws Exception {
            OCSPReqBuilder builder = new OCSPReqBuilder();
            Arrays.stream(ids).forEach(builder::addRequest);
            builder.setRequestExtensions(new Extensions(
                    new Extension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce, false, new DEROctetString(nonce))));
            return builder.build().getEncoded();
        }
    }
}
