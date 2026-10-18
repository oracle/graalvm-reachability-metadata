/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.cmp.CMPCertificate;
import org.bouncycastle.asn1.cmp.CMPObjectIdentifiers;
import org.bouncycastle.asn1.cmp.CertResponse;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIConfirmContent;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIFreeText;
import org.bouncycastle.asn1.cmp.PKIHeader;
import org.bouncycastle.asn1.cmp.PKIStatus;
import org.bouncycastle.asn1.cmp.PKIStatusInfo;
import org.bouncycastle.asn1.crmf.CertTemplate;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.cmp.CertificateConfirmationContent;
import org.bouncycastle.cert.cmp.CertificateConfirmationContentBuilder;
import org.bouncycastle.cert.cmp.CertificateStatus;
import org.bouncycastle.cert.cmp.GeneralPKIMessage;
import org.bouncycastle.cert.cmp.ProtectedPKIMessage;
import org.bouncycastle.cert.cmp.ProtectedPKIMessageBuilder;
import org.bouncycastle.cert.crmf.CertificateRepMessage;
import org.bouncycastle.cert.crmf.CertificateRepMessageBuilder;
import org.bouncycastle.cert.crmf.CertificateReqMessages;
import org.bouncycastle.cert.crmf.CertificateReqMessagesBuilder;
import org.bouncycastle.cert.crmf.CertificateRequestMessage;
import org.bouncycastle.cert.crmf.CertificateResponse;
import org.bouncycastle.cert.crmf.CertificateResponseBuilder;
import org.bouncycastle.cert.crmf.PKMACBuilder;
import org.bouncycastle.cert.crmf.jcajce.JcaCertificateRequestMessage;
import org.bouncycastle.cert.crmf.jcajce.JcaCertificateRequestMessageBuilder;
import org.bouncycastle.cert.crmf.jcajce.JcePKMACValuesCalculator;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs CMP certificate management with CRMF request messages and password-based MAC protection.
 *
 * A branch-office router enrolls with the corporate CA over CMP (RFC 4210). The operator gave
 * it a reference number and a one-time shared secret out of band. The router sends an
 * initialization request holding a CRMF certificate request message with a signature proof of
 * possession, protected by the CMP password-based MAC keyed with the shared secret. The
 * CA checks the MAC and the proof of possession, issues the certificate in an initialization
 * response, and the router confirms the certificate by hash in a certConf message that the CA
 * acknowledges with a pkiConf.
 */
class RouterCmpEnrollmentTest {

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    private static final byte[] REFERENCE = "REF-4711".getBytes(StandardCharsets.US_ASCII);

    private static final char[] SHARED_SECRET = "insta-Pr0vision-9Z".toCharArray();

    private static final X500Name CA_NAME = new X500Name("CN=Acme Network CA,O=Acme,C=DE");

    private static final X500Principal ROUTER_NAME =
            new X500Principal("CN=rtr-branch-17.net.acme.test,OU=Branch Routers,O=Acme,C=DE");

    private static final String ROUTER_HOST = "rtr-branch-17.net.acme.test";

    @Test
    void routerEnrollsAndConfirmsCertificate() throws Exception {
        CertificateAuthority authority = newAuthority();
        Router router = new Router(newKeyPair(), SHARED_SECRET);

        byte[] initRequest = router.initializationRequest();
        GeneralPKIMessage onWire = new GeneralPKIMessage(initRequest);
        assertThat(onWire.hasProtection()).isTrue();
        assertThat(onWire.getBody().getType()).isEqualTo(PKIBody.TYPE_INIT_REQ);
        assertThat(onWire.getHeader().getProtectionAlg().getAlgorithm())
                .isEqualTo(CMPObjectIdentifiers.passwordBasedMac);

        Reply reply = router.receiveInitializationResponse(authority.handle(initRequest));

        assertThat(reply.protectionValid()).isTrue();
        assertThat(reply.bodyType()).isEqualTo(PKIBody.TYPE_INIT_REP);
        assertThat(reply.status()).isEqualTo(PKIStatus.GRANTED);
        assertThat(reply.failInfo()).isZero();
        X509CertificateHolder issued = reply.certificate();
        assertThat(issued).isNotNull();
        assertThat(issued.getSubject()).isEqualTo(X500Name.getInstance(ROUTER_NAME.getEncoded()));
        assertThat(issued.getIssuer()).isEqualTo(CA_NAME);
        assertThat(issued.getSubjectPublicKeyInfo().getEncoded())
                .isEqualTo(router.keys().getPublic().getEncoded());
        assertThat(issued.isSignatureValid(new JcaContentVerifierProviderBuilder()
                .build(reply.caCertificate()))).isTrue();
        GeneralName[] names = GeneralNames.fromExtensions(issued.getExtensions(),
                Extension.subjectAlternativeName).getNames();
        assertThat(names).hasSize(1);
        assertThat(((ASN1String) names[0].getName()).getString()).isEqualTo(ROUTER_HOST);

        byte[] certConf = router.certificateConfirmation(reply);
        Reply acknowledgement = router.receiveAcknowledgement(authority.handle(certConf));

        assertThat(acknowledgement.protectionValid()).isTrue();
        assertThat(acknowledgement.bodyType()).isEqualTo(PKIBody.TYPE_CONFIRM);
        assertThat(authority.confirmedRequestIds()).containsExactly(BigInteger.ZERO);
    }

    @Test
    void caRejectsRequestProtectedWithWrongSharedSecret() throws Exception {
        CertificateAuthority authority = newAuthority();
        Router router = new Router(newKeyPair(), "guessed-secret".toCharArray());

        Reply reply = router.receiveInitializationResponse(
                authority.handle(router.initializationRequest()));

        assertThat(reply.protectionValid()).isFalse();
        assertThat(reply.status()).isEqualTo(PKIStatus.REJECTION);
        assertThat(reply.failInfo() & PKIFailureInfo.badMessageCheck).isNotZero();
        assertThat(reply.statusText()).isEqualTo("message protection check failed");
        assertThat(reply.certificate()).isNull();
        assertThat(authority.issuedCount()).isZero();
    }

    @Test
    void caRejectsRequestWhoseProofOfPossessionUsesAnotherKey() throws Exception {
        CertificateAuthority authority = newAuthority();
        KeyPair stolenPublicKey = newKeyPair();
        Router router = new Router(newKeyPair(), SHARED_SECRET);

        Reply reply = router.receiveInitializationResponse(authority.handle(
                router.initializationRequest(stolenPublicKey.getPublic())));

        assertThat(reply.protectionValid()).isTrue();
        assertThat(reply.status()).isEqualTo(PKIStatus.REJECTION);
        assertThat(reply.failInfo() & PKIFailureInfo.badPOP).isNotZero();
        assertThat(reply.statusText()).isEqualTo("proof of possession failed");
        assertThat(reply.certificate()).isNull();
        assertThat(authority.issuedCount()).isZero();
    }

    private static CertificateAuthority newAuthority() throws Exception {
        return new CertificateAuthority(Map.of("REF-4711", SHARED_SECRET));
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static ContentSigner signer(KeyPair keys) throws Exception {
        return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keys.getPrivate());
    }

    private static PKMACBuilder pkmac() {
        return new PKMACBuilder(new JcePKMACValuesCalculator()).setIterationCount(1024);
    }

    private static byte[] nonce() {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    private static DigestCalculatorProvider digests() throws Exception {
        return new JcaDigestCalculatorProviderBuilder().build();
    }

    private static byte[] octets(ASN1OctetString value) {
        return value == null ? null : value.getOctets();
    }

    /** What the router learned from one CA reply. */
    private record Reply(boolean protectionValid, int bodyType, int status, int failInfo,
            String statusText, X509CertificateHolder certificate,
            X509CertificateHolder caCertificate, BigInteger certReqId, byte[] caNonce) {
    }

    /** The end entity: it owns the key pair and the shared secret it was provisioned with. */
    private static final class Router {

        private final KeyPair keys;

        private final char[] secret;

        private final byte[] transactionId = nonce();

        private byte[] lastNonce;

        Router(KeyPair keys, char[] secret) {
            this.keys = keys;
            this.secret = secret;
        }

        KeyPair keys() {
            return keys;
        }

        byte[] initializationRequest() throws Exception {
            return initializationRequest(keys.getPublic());
        }

        /** Builds an ir for `requestedKey`, signing the proof of possession with its own key. */
        byte[] initializationRequest(PublicKey requestedKey) throws Exception {
            JcaCertificateRequestMessageBuilder request =
                    new JcaCertificateRequestMessageBuilder(BigInteger.ZERO);
            request.setSubject(ROUTER_NAME).setPublicKey(requestedKey);
            request.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, ROUTER_HOST)));
            request.setProofOfPossessionSigningKeySigner(signer(keys));

            CertificateReqMessagesBuilder messages = new CertificateReqMessagesBuilder();
            messages.addRequest(request.build());
            return protect(new ProtectedPKIMessageBuilder(
                    new GeneralName(X500Name.getInstance(ROUTER_NAME.getEncoded())),
                    new GeneralName(CA_NAME))
                    .setBody(PKIBody.TYPE_INIT_REQ, messages.build()));
        }

        Reply receiveInitializationResponse(byte[] der) throws Exception {
            ProtectedPKIMessage message = open(der);
            CertificateRepMessage repMessage = CertificateRepMessage.fromPKIBody(message.getBody());
            CertificateResponse response = repMessage.getResponses()[0];
            CertResponse asn1 = response.toASN1Structure();
            PKIStatusInfo info = asn1.getStatus();
            PKIFreeText text = info.getStatusString();
            X509CertificateHolder certificate = null;
            if (asn1.getCertifiedKeyPair() != null) {
                CMPCertificate cmpCertificate = response.getCertificate();
                certificate = new X509CertificateHolder(cmpCertificate.getX509v3PKCert());
            }
            X509CertificateHolder[] caPubs = repMessage.getX509Certificates();
            return new Reply(verify(message), message.getBody().getType(),
                    info.getStatus().intValue(),
                    info.getFailInfo() == null ? 0 : info.getFailInfo().intValue(),
                    text == null ? null : text.getStringAtUTF8(0).getString(), certificate,
                    caPubs.length == 0 ? null : caPubs[0], asn1.getCertReqId().getValue(),
                    octets(message.getHeader().getSenderNonce()));
        }

        byte[] certificateConfirmation(Reply reply) throws Exception {
            CertificateConfirmationContent content = new CertificateConfirmationContentBuilder()
                    .addAcceptedCertificate(reply.certificate(), reply.certReqId())
                    .build(digests());
            return protect(new ProtectedPKIMessageBuilder(
                    new GeneralName(X500Name.getInstance(ROUTER_NAME.getEncoded())),
                    new GeneralName(CA_NAME))
                    .setRecipNonce(reply.caNonce())
                    .setBody(PKIBody.TYPE_CERT_CONFIRM, content));
        }

        Reply receiveAcknowledgement(byte[] der) throws Exception {
            ProtectedPKIMessage message = open(der);
            return new Reply(verify(message), message.getBody().getType(), PKIStatus.GRANTED, 0,
                    null, null, null, null, octets(message.getHeader().getSenderNonce()));
        }

        private byte[] protect(ProtectedPKIMessageBuilder builder) throws Exception {
            lastNonce = nonce();
            return builder.setTransactionID(transactionId)
                    .setSenderNonce(lastNonce)
                    .setSenderKID(REFERENCE)
                    .setMessageTime(new Date())
                    .build(pkmac().build(secret))
                    .toASN1Structure()
                    .getEncoded();
        }

        private ProtectedPKIMessage open(byte[] der) throws Exception {
            ProtectedPKIMessage message = new ProtectedPKIMessage(new GeneralPKIMessage(der));
            PKIHeader header = message.getHeader();
            assertThat(octets(header.getTransactionID())).isEqualTo(transactionId);
            assertThat(octets(header.getRecipNonce())).isEqualTo(lastNonce);
            return message;
        }

        private boolean verify(ProtectedPKIMessage message) throws Exception {
            return message.hasPasswordBasedMacProtection() && message.verify(pkmac(), secret);
        }
    }

    /** The CA: it knows each reference number's shared secret and issues from CRMF templates. */
    private static final class CertificateAuthority {

        private final Map<String, char[]> secrets;

        private final KeyPair caKeys = newKeyPair();

        private final X509CertificateHolder caCertificate;

        private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        private final List<X509CertificateHolder> issued = new ArrayList<>();

        private final List<BigInteger> confirmed = new ArrayList<>();

        CertificateAuthority(Map<String, char[]> secrets) throws Exception {
            this.secrets = secrets;
            this.caCertificate = new JcaX509v3CertificateBuilder(CA_NAME, BigInteger.ONE,
                    Date.from(now.minus(1, ChronoUnit.HOURS)), Date.from(now.plus(3650,
                    ChronoUnit.DAYS)), CA_NAME, caKeys.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .build(signer(caKeys));
        }

        int issuedCount() {
            return issued.size();
        }

        List<BigInteger> confirmedRequestIds() {
            return confirmed;
        }

        byte[] handle(byte[] der) throws Exception {
            ProtectedPKIMessage message = new ProtectedPKIMessage(new GeneralPKIMessage(der));
            PKIHeader header = message.getHeader();
            char[] secret = secrets.get(new String(octets(header.getSenderKID()),
                    StandardCharsets.US_ASCII));
            assertThat(secret).isNotNull();
            boolean authentic = message.hasPasswordBasedMacProtection()
                    && message.verify(pkmac(), secret);
            int type = message.getBody().getType();

            if (type == PKIBody.TYPE_CERT_CONFIRM) {
                assertThat(authentic).isTrue();
                for (CertificateStatus status : CertificateConfirmationContent
                        .fromPKIBody(message.getBody()).getStatusMessages()) {
                    X509CertificateHolder certificate =
                            issued.get(status.getCertRequestID().intValue());
                    if (status.isVerified(certificate, digests())) {
                        confirmed.add(status.getCertRequestID());
                    }
                }
                return reply(header, secret,
                        new PKIBody(PKIBody.TYPE_CONFIRM, new PKIConfirmContent()));
            }

            CertificateRequestMessage request =
                    CertificateReqMessages.fromPKIBody(message.getBody()).getRequests()[0];
            ASN1Integer certReqId = request.getCertReqId();
            CertificateResponse response;
            if (!authentic) {
                response = rejection(certReqId, "message protection check failed",
                        PKIFailureInfo.badMessageCheck);
            } else if (!proofOfPossession(request)) {
                response = rejection(certReqId, "proof of possession failed",
                        PKIFailureInfo.badPOP);
            } else {
                X509CertificateHolder certificate = issue(request.getCertTemplate());
                issued.add(certificate);
                response = new CertificateResponseBuilder(certReqId,
                        new PKIStatusInfo(PKIStatus.granted))
                        .withCertificate(certificate)
                        .build();
            }
            CertificateRepMessage repMessage = new CertificateRepMessageBuilder(caCertificate)
                    .addCertificateResponse(response)
                    .build();
            return reply(header, secret, PKIBody.TYPE_INIT_REP, repMessage);
        }

        private boolean proofOfPossession(CertificateRequestMessage request) throws Exception {
            JcaCertificateRequestMessage jcaRequest = new JcaCertificateRequestMessage(request);
            return request.hasProofOfPossession()
                    && request.getProofOfPossessionType() == CertificateRequestMessage.popSigningKey
                    && request.isValidSigningKeyPOP(new JcaContentVerifierProviderBuilder()
                            .build(jcaRequest.getPublicKey()));
        }

        private X509CertificateHolder issue(CertTemplate template) throws Exception {
            X509v3CertificateBuilder builder = new X509v3CertificateBuilder(CA_NAME,
                    BigInteger.valueOf(1000 + issued.size()), Date.from(now),
                    Date.from(now.plus(365, ChronoUnit.DAYS)), template.getSubject(),
                    template.getPublicKey());
            builder.addExtension(template.getExtensions()
                    .getExtension(Extension.subjectAlternativeName));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            return builder.build(signer(caKeys));
        }

        private static CertificateResponse rejection(ASN1Integer certReqId, String reason,
                int failure) {
            return new CertificateResponseBuilder(certReqId, new PKIStatusInfo(
                    PKIStatus.rejection, new PKIFreeText(reason), new PKIFailureInfo(failure)))
                    .build();
        }

        private byte[] reply(PKIHeader request, char[] secret, int bodyType,
                CertificateRepMessage content) throws Exception {
            return start(request).setBody(bodyType, content)
                    .build(pkmac().build(secret)).toASN1Structure().getEncoded();
        }

        private byte[] reply(PKIHeader request, char[] secret, PKIBody body) throws Exception {
            return start(request).setBody(body)
                    .build(pkmac().build(secret)).toASN1Structure().getEncoded();
        }

        private static ProtectedPKIMessageBuilder start(PKIHeader request) {
            return new ProtectedPKIMessageBuilder(new GeneralName(CA_NAME), request.getSender())
                    .setTransactionID(octets(request.getTransactionID()))
                    .setRecipNonce(octets(request.getSenderNonce()))
                    .setSenderNonce(nonce())
                    .setRecipKID(octets(request.getSenderKID()))
                    .setMessageTime(new Date());
        }
    }
}
