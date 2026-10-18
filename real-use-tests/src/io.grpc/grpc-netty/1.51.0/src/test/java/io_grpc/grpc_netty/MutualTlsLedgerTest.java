/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.StringValue;
import io.grpc.CallOptions;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Secures a branch-ledger service with mutual TLS through `GrpcSslContexts`, negotiating
 * HTTP/2 over ALPN on the grpc-netty server and channel and identifying callers by their
 * client certificate.
 *
 * A bank's head office issues a private certificate authority with BouncyCastle, signs a
 * server certificate for `ledger.internal` and a client certificate for each branch, and
 * requires every branch to present one. The server reads the verified peer certificate from
 * the TLS session, so a branch only ever sees its own balance, while a client that trusts a
 * different authority cannot reach the ledger at all.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MutualTlsLedgerTest {

    private static final String SERVICE = "bank.BranchLedger";

    private static final String AUTHORITY = "ledger.internal";

    private static final Context.Key<String> BRANCH = Context.key("branch");

    private static final MethodDescriptor<StringValue, StringValue> BALANCE =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Balance"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private final Ledger ledger = new Ledger(Map.of("branch-07", 420_000L, "branch-12", 87_500L));

    private CertificateAuthority headOffice;

    private Server server;

    @BeforeAll
    void startTlsServer() throws Exception {
        headOffice = new CertificateAuthority("CN=Head Office Root CA,O=Example Bank");
        Issued serverIdentity = headOffice.issue("CN=" + AUTHORITY + ",O=Example Bank", AUTHORITY);

        SslContext serverTls = GrpcSslContexts.configure(
                        SslContextBuilder.forServer(serverIdentity.key(), serverIdentity.certificate()))
                .trustManager(headOffice.certificate())
                .clientAuth(ClientAuth.REQUIRE)
                .build();
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .sslContext(serverTls)
                .addService(ServerInterceptors.intercept(ledger.bindService(), new BranchIdentityInterceptor()))
                .build()
                .start();
    }

    @AfterAll
    void stopTlsServer() throws Exception {
        server.shutdown();
        assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void eachBranchSeesOnlyItsOwnBalance() throws Exception {
        ManagedChannel branch07 = branchChannel(headOffice, headOffice.issue("CN=branch-07,O=Example Bank", null));
        ManagedChannel branch12 = branchChannel(headOffice, headOffice.issue("CN=branch-12,O=Example Bank", null));
        try {
            assertThat(balance(branch07).getValue()).isEqualTo("branch-07 over TLS: 420000");
            assertThat(balance(branch12).getValue()).isEqualTo("branch-12 over TLS: 87500");
            assertThat(balance(branch07).getValue()).isEqualTo("branch-07 over TLS: 420000");
            assertThat(ledger.servedCalls()).isEqualTo(3L);
        } finally {
            close(branch07);
            close(branch12);
        }
    }

    @Test
    void refusesALedgerSignedByAnUnknownAuthority() throws Exception {
        CertificateAuthority impostor = new CertificateAuthority("CN=Impostor Root CA,O=Elsewhere");
        ManagedChannel misconfigured = branchChannel(impostor, impostor.issue("CN=branch-07,O=Example Bank", null));
        try {
            assertThatThrownBy(() -> balance(misconfigured))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                            assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
        } finally {
            close(misconfigured);
        }
    }

    private ManagedChannel branchChannel(CertificateAuthority trusted, Issued identity) throws Exception {
        SslContext clientTls = GrpcSslContexts.forClient()
                .keyManager(identity.key(), identity.certificate())
                .trustManager(trusted.certificate())
                .build();
        return NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                .sslContext(clientTls)
                .overrideAuthority(AUTHORITY)
                .build();
    }

    private static StringValue balance(ManagedChannel channel) {
        return ClientCalls.blockingUnaryCall(channel, BALANCE,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), StringValue.of("current"));
    }

    private static void close(ManagedChannel channel) throws InterruptedException {
        channel.shutdownNow();
        assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    /** A private key with the certificate the head office issued for it. */
    private record Issued(PrivateKey key, X509Certificate certificate) {
    }

    /** A self-signed root that issues server and branch certificates with BouncyCastle. */
    private static final class CertificateAuthority {

        private static final AtomicLong SERIALS = new AtomicLong(1);

        private final X500Name name;

        private final KeyPair keys;

        private final X509Certificate certificate;

        CertificateAuthority(String subject) throws Exception {
            name = new X500Name(subject);
            keys = newKeyPair();
            X509v3CertificateBuilder builder = newBuilder(name, keys)
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .addExtension(Extension.keyUsage, true,
                            new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            certificate = sign(builder, keys.getPrivate());
        }

        X509Certificate certificate() {
            return certificate;
        }

        Issued issue(String subject, String dnsName) throws Exception {
            KeyPair leaf = newKeyPair();
            X509v3CertificateBuilder builder = newBuilder(new X500Name(subject), leaf)
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            if (dnsName != null) {
                builder.addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(new GeneralName(GeneralName.dNSName, dnsName)));
            }
            return new Issued(leaf.getPrivate(), sign(builder, keys.getPrivate()));
        }

        private JcaX509v3CertificateBuilder newBuilder(X500Name subject, KeyPair subjectKeys) {
            Instant now = Instant.now();
            return new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(SERIALS.getAndIncrement()),
                    Date.from(now.minus(Duration.ofHours(1))), Date.from(now.plus(Duration.ofDays(1))),
                    subject, subjectKeys.getPublic());
        }

        private static X509Certificate sign(X509v3CertificateBuilder builder, PrivateKey signer)
                throws Exception {
            return new JcaX509CertificateConverter()
                    .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(signer)));
        }

        private static KeyPair newKeyPair() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            return generator.generateKeyPair();
        }
    }

    /** Puts the common name of the verified client certificate into the call context. */
    private static final class BranchIdentityInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
            if (session == null) {
                call.close(Status.UNAUTHENTICATED.withDescription("TLS required"), new Metadata());
                return new ServerCall.Listener<ReqT>() {
                };
            }
            try {
                X500Name subject = new X500Name(session.getPeerPrincipal().getName());
                String commonName = subject.getRDNs(BCStyle.CN)[0]
                        .getFirst().getValue().toString();
                return Contexts.interceptCall(Context.current().withValue(BRANCH, commonName), call, headers, next);
            } catch (SSLPeerUnverifiedException e) {
                call.close(Status.UNAUTHENTICATED.withDescription("no client certificate"), new Metadata());
                return new ServerCall.Listener<ReqT>() {
                };
            }
        }
    }

    /** Branch balances, answered for the branch the client certificate names. */
    private static final class Ledger {

        private final Map<String, Long> balances;

        private final AtomicLong served = new AtomicLong();

        Ledger(Map<String, Long> balances) {
            this.balances = balances;
        }

        long servedCalls() {
            return served.get();
        }

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(BALANCE, ServerCalls.asyncUnaryCall(this::balance))
                    .build();
        }

        private void balance(StringValue request, StreamObserver<StringValue> response) {
            String branch = BRANCH.get();
            served.incrementAndGet();
            response.onNext(StringValue.of(branch + " over TLS: " + balances.get(branch)));
            response.onCompleted();
        }
    }
}
