/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk18on;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
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
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.bouncycastle.tsp.ers.ArchiveTimeStampValidationException;
import org.bouncycastle.tsp.ers.ERSArchiveTimeStamp;
import org.bouncycastle.tsp.ers.ERSArchiveTimeStampGenerator;
import org.bouncycastle.tsp.ers.ERSByteData;
import org.bouncycastle.tsp.ers.ERSData;
import org.bouncycastle.tsp.ers.ERSDataGroup;
import org.bouncycastle.tsp.ers.ERSEvidenceRecord;
import org.bouncycastle.tsp.ers.ERSEvidenceRecordGenerator;
import org.bouncycastle.tsp.ers.ERSEvidenceRecordSelector;
import org.bouncycastle.tsp.ers.ERSEvidenceRecordStore;
import org.bouncycastle.tsp.ers.ERSException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
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
 * Preserves long-term proof of existence for archived documents with RFC 4998 evidence records
 * from the bcpkix ERS archive time-stamp generator, evidence record generator, and evidence
 * record store.
 *
 * A contract archive seals a whole batch of invoices and a contract with its annex under one
 * time stamp: the archive hashes the batch into a Merkle tree, asks its Time-Stamp Authority to
 * stamp the root, and keeps one evidence record per document. Years later an auditor looks a
 * document up in the record store and checks that it is the one that was sealed. Before the
 * TSA key expires the archive renews the time stamp with the successor TSA, and when SHA-256 is
 * retired it renews the hash tree under SHA-512, so each record stays verifiable.
 */
class ContractArchiveEvidenceRecordTest {

    private static final ASN1ObjectIdentifier ARCHIVE_POLICY = new ASN1ObjectIdentifier("1.3.6.1.4.1.55555.2.1");

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final Instant sealedAt = now.minus(900, ChronoUnit.DAYS);

    private final Instant renewedAt = now.minus(30, ChronoUnit.DAYS);

    private final Instant rehashedAt = now.minus(1, ChronoUnit.DAYS);

    @Test
    void archiveSealsBatchAndAuditorFindsEachDocument() throws Exception {
        DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();
        TimeStampAuthority tsa = TimeStampAuthority.validAround("CN=Acme Archive TSA 2024, O=Acme Corp", sealedAt);
        Batch batch = new Batch();

        List<ERSEvidenceRecord> records = seal(digests, tsa, batch, sealedAt);
        assertThat(records).hasSize(4);

        ERSEvidenceRecordStore store = new ERSEvidenceRecordStore(records);
        assertThat(store.getMatches(null)).hasSize(4);
        for (ERSData document : batch.all()) {
            Collection<ERSEvidenceRecord> matches = store.getMatches(new ERSEvidenceRecordSelector(document));
            assertThat(matches).hasSize(1);
            ERSEvidenceRecord record = matches.iterator().next();
            record.validatePresent(document, Date.from(now));
            assertThat(record.isContaining(document, Date.from(now))).isTrue();
            assertThat(record.getSigningCertificate().getSubject()).isEqualTo(tsa.certificate().getSubject());
            record.validate(verifierFor(record.getSigningCertificate()));
        }

        ERSEvidenceRecord first = records.get(0);
        for (ERSEvidenceRecord other : records) {
            assertThat(other.isRelatedTo(first)).isTrue();
            assertThat(other.getPrimaryRootHash()).isEqualTo(first.getPrimaryRootHash());
        }

        ERSEvidenceRecord invoiceRecord = recordFor(store, batch.invoiceMarch);
        ERSEvidenceRecord reloaded = new ERSEvidenceRecord(invoiceRecord.getEncoded(), digests);
        reloaded.validatePresent(batch.invoiceMarch, Date.from(now));
        reloaded.validate(verifierFor(tsa.certificate()));
        assertThat(reloaded.isContaining(batch.invoiceApril, Date.from(now))).isFalse();
    }

    @Test
    void auditorDetectsAlteredDocumentsAndPrematureClaims() throws Exception {
        DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();
        TimeStampAuthority tsa = TimeStampAuthority.validAround("CN=Acme Archive TSA 2024, O=Acme Corp", sealedAt);
        Batch batch = new Batch();
        ERSEvidenceRecordStore store = new ERSEvidenceRecordStore(seal(digests, tsa, batch, sealedAt));
        ERSEvidenceRecord invoiceRecord = recordFor(store, batch.invoiceMarch);

        ERSData altered = document("""
                INVOICE 2024-03-0117
                Customer: Globex Corporation
                Amount due: EUR 1,940.00
                """);
        assertThat(store.getMatches(new ERSEvidenceRecordSelector(altered))).isEmpty();
        assertThat(invoiceRecord.isContaining(altered, Date.from(now))).isFalse();
        assertThatThrownBy(() -> invoiceRecord.validatePresent(altered, Date.from(now)))
                .isInstanceOf(ArchiveTimeStampValidationException.class)
                .hasMessageContaining("not found");

        ERSData contractOnly = new ERSDataGroup(batch.contract);
        assertThat(store.getMatches(new ERSEvidenceRecordSelector(contractOnly))).isEmpty();

        Date beforeSealing = Date.from(sealedAt.minus(1, ChronoUnit.DAYS));
        assertThatThrownBy(() -> invoiceRecord.validatePresent(batch.invoiceMarch, beforeSealing))
                .isInstanceOf(ArchiveTimeStampValidationException.class)
                .hasMessageContaining("future");
        assertThat(store.getMatches(new ERSEvidenceRecordSelector(batch.invoiceMarch, beforeSealing))).isEmpty();

        TimeStampAuthority impostor = TimeStampAuthority.validAround("CN=Rogue TSA, O=Nobody", sealedAt);
        assertThatThrownBy(() -> invoiceRecord.validate(verifierFor(impostor.certificate())))
                .isInstanceOf(TSPException.class);
    }

    @Test
    void archiveRenewsTimeStampWithSuccessorTsa() throws Exception {
        DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();
        TimeStampAuthority retiring = TimeStampAuthority.validAround("CN=Acme Archive TSA 2024, O=Acme Corp", sealedAt);
        TimeStampAuthority successor = TimeStampAuthority.validAround("CN=Acme Archive TSA 2026, O=Acme Corp",
                renewedAt);
        Batch batch = new Batch();
        ERSEvidenceRecordStore store = new ERSEvidenceRecordStore(seal(digests, retiring, batch, sealedAt));
        ERSEvidenceRecord original = recordFor(store, batch.contractWithAnnex);

        TimeStampRequest renewalRequest = original.generateTimeStampRenewalRequest(requestGenerator(),
                BigInteger.valueOf(42));
        assertThat(renewalRequest.getMessageImprintAlgOID()).isEqualTo(TSPAlgorithms.SHA256);
        TimeStampResponse renewalResponse = successor.respond(renewalRequest, BigInteger.valueOf(2002), renewedAt);
        renewalResponse.validate(renewalRequest);

        ERSEvidenceRecord renewed = new ERSEvidenceRecord(original.renewTimeStamp(renewalResponse).getEncoded(),
                digests);
        assertThat(renewed.isRelatedTo(original)).isTrue();
        assertThat(renewed.getPrimaryRootHash()).isEqualTo(original.getPrimaryRootHash());
        assertThat(renewed.getSigningCertificate().getSubject()).isEqualTo(successor.certificate().getSubject());
        renewed.validatePresent(batch.contractWithAnnex, Date.from(now));
        renewed.validate(verifierFor(successor.certificate()));
        assertThatThrownBy(() -> renewed.validate(verifierFor(retiring.certificate())))
                .isInstanceOf(TSPException.class);

        TimeStampRequest unrelated = requestGenerator().generate(TSPAlgorithms.SHA256, new byte[32]);
        TimeStampResponse unrelatedResponse = successor.respond(unrelated, BigInteger.valueOf(2003), renewedAt);
        assertThatThrownBy(() -> original.renewTimeStamp(unrelatedResponse))
                .isInstanceOf(ERSException.class)
                .hasMessageContaining("wrong root hash");
    }

    @Test
    void archiveRenewsHashTreeUnderStrongerDigest() throws Exception {
        DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();
        TimeStampAuthority retiring = TimeStampAuthority.validAround("CN=Acme Archive TSA 2024, O=Acme Corp", sealedAt);
        TimeStampAuthority successor = TimeStampAuthority.validAround("CN=Acme Archive TSA 2026, O=Acme Corp",
                rehashedAt);
        Batch batch = new Batch();
        ERSEvidenceRecordStore store = new ERSEvidenceRecordStore(seal(digests, retiring, batch, sealedAt));
        ERSEvidenceRecord original = recordFor(store, batch.invoiceApril);

        DigestCalculator sha512 = digests.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha512));
        TimeStampRequest rehashRequest = original.generateHashRenewalRequest(sha512, batch.invoiceApril,
                requestGenerator(), BigInteger.valueOf(77));
        assertThat(rehashRequest.getMessageImprintAlgOID()).isEqualTo(TSPAlgorithms.SHA512);
        TimeStampResponse rehashResponse = successor.respond(rehashRequest, BigInteger.valueOf(3003), rehashedAt);

        ERSEvidenceRecord rehashed = new ERSEvidenceRecord(
                original.renewHash(sha512, batch.invoiceApril, rehashResponse).getEncoded(), digests);
        assertThat(rehashed.isRelatedTo(original)).isTrue();
        rehashed.validatePresent(batch.invoiceApril, Date.from(now));
        assertThat(rehashed.isContaining(batch.invoiceMarch, Date.from(now))).isFalse();
        assertThat(rehashed.getSigningCertificate().getSubject()).isEqualTo(successor.certificate().getSubject());
        rehashed.validate(verifierFor(successor.certificate()));

        assertThatThrownBy(() -> original.generateHashRenewalRequest(sha512, batch.invoiceMarch, requestGenerator()))
                .isInstanceOf(ERSException.class)
                .hasMessageContaining("invalid data");
    }

    /** Hashes the batch into one tree, has the TSA stamp its root, and issues one record per document. */
    private static List<ERSEvidenceRecord> seal(DigestCalculatorProvider digests, TimeStampAuthority tsa,
            Batch batch, Instant genTime) throws Exception {
        DigestCalculator sha256 = digests.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256));
        ERSArchiveTimeStampGenerator generator = new ERSArchiveTimeStampGenerator(sha256);
        generator.addAllData(batch.all());

        BigInteger nonce = BigInteger.valueOf(0x5EA1EDL);
        TimeStampRequest request = generator.generateTimeStampRequest(requestGenerator(), nonce);
        assertThat(request.getNonce()).isEqualTo(nonce);
        TimeStampResponse response = tsa.respond(request, BigInteger.valueOf(1001), genTime);
        response.validate(request);

        List<ERSArchiveTimeStamp> archiveTimeStamps = generator.generateArchiveTimeStamps(response);
        assertThat(archiveTimeStamps).hasSize(4);
        for (ERSArchiveTimeStamp archiveTimeStamp : archiveTimeStamps) {
            assertThat(archiveTimeStamp.getGenTime()).isEqualTo(Date.from(genTime));
            assertThat(archiveTimeStamp.getDigestAlgorithmIdentifier().getAlgorithm())
                    .isEqualTo(NISTObjectIdentifiers.id_sha256);
            assertThat(archiveTimeStamp.getTimeStampToken().getTimeStampInfo().getMessageImprintDigest())
                    .isEqualTo(request.getMessageImprintDigest());
        }
        return new ERSEvidenceRecordGenerator(digests).generate(archiveTimeStamps);
    }

    private static ERSEvidenceRecord recordFor(ERSEvidenceRecordStore store, ERSData document) throws Exception {
        Collection<ERSEvidenceRecord> matches = store.getMatches(new ERSEvidenceRecordSelector(document));
        assertThat(matches).hasSize(1);
        return matches.iterator().next();
    }

    private static TimeStampRequestGenerator requestGenerator() {
        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true);
        generator.setReqPolicy(ARCHIVE_POLICY);
        return generator;
    }

    private static SignerInformationVerifier verifierFor(X509CertificateHolder certificate) throws Exception {
        return new JcaSimpleSignerInfoVerifierBuilder().build(certificate);
    }

    private static ERSByteData document(String text) {
        return new ERSByteData(text.getBytes(StandardCharsets.UTF_8));
    }

    /** The documents sealed together: three invoices and a contract archived with its annex. */
    private static final class Batch {

        final ERSData invoiceMarch = document("""
                INVOICE 2024-03-0117
                Customer: Globex Corporation
                Amount due: EUR 1,490.00
                """);

        final ERSData invoiceApril = document("""
                INVOICE 2024-04-0233
                Customer: Initech LLC
                Amount due: EUR 820.50
                """);

        final ERSData invoiceMay = document("""
                INVOICE 2024-05-0301
                Customer: Umbrella plc
                Amount due: EUR 12,000.00
                """);

        final ERSData contract = document("""
                SERVICE AGREEMENT 2024-0042
                Supplier: Acme Hosting Ltd.
                Customer: Globex Corporation
                """);

        final ERSData annex = document("""
                ANNEX A - SERVICE LEVELS
                Availability: 99.95 percent per calendar month.
                """);

        final ERSData contractWithAnnex = new ERSDataGroup(contract, annex);

        List<ERSData> all() {
            return List.of(invoiceMarch, invoiceApril, invoiceMay, contractWithAnnex);
        }
    }

    /**
     * A Time-Stamp Authority whose certificate is restricted to time stamping and valid for three
     * years from shortly before the time it stamps, accepting SHA-256 and SHA-512 imprints under
     * the archive policy.
     */
    private static final class TimeStampAuthority {

        private final X509CertificateHolder certificate;

        private final TimeStampResponseGenerator responder;

        private TimeStampAuthority(X500Name name, KeyPair keys, Instant notBefore, Instant notAfter) throws Exception {
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name,
                    BigInteger.valueOf(notBefore.getEpochSecond()), Date.from(notBefore), Date.from(notAfter), name,
                    keys.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(Extension.extendedKeyUsage, true,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate());
            this.certificate = builder.build(signer);

            DigestCalculatorProvider digests = new JcaDigestCalculatorProviderBuilder().build();
            TimeStampTokenGenerator tokens = new TimeStampTokenGenerator(
                    new JcaSignerInfoGeneratorBuilder(digests).build(signer, certificate),
                    digests.get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)), ARCHIVE_POLICY);
            tokens.addCertificates(new JcaCertStore(List.of(certificate)));
            this.responder = new TimeStampResponseGenerator(tokens,
                    Set.of(TSPAlgorithms.SHA256, TSPAlgorithms.SHA512), Set.of(ARCHIVE_POLICY));
        }

        static TimeStampAuthority validAround(String name, Instant stampTime) throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return new TimeStampAuthority(new X500Name(name), generator.generateKeyPair(),
                    stampTime.minus(1, ChronoUnit.DAYS), stampTime.plus(3 * 365, ChronoUnit.DAYS));
        }

        X509CertificateHolder certificate() {
            return certificate;
        }

        TimeStampResponse respond(TimeStampRequest request, BigInteger serialNumber, Instant genTime) throws Exception {
            byte[] encoded = responder.generate(new TimeStampRequest(request.getEncoded()), serialNumber,
                    Date.from(genTime)).getEncoded();
            return new TimeStampResponse(encoded);
        }
    }
}
