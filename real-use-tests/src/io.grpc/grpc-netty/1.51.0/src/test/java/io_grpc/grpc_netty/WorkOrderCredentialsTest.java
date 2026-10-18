/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty;

import com.google.protobuf.StringValue;
import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.ChannelCredentials;
import io.grpc.CompositeChannelCredentials;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.SecurityLevel;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerCredentials;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.TlsServerCredentials;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Secures a work-order dispatch service through the gRPC credentials API, where grpc-netty turns
 * `TlsServerCredentials`, `TlsChannelCredentials`, `InsecureServerCredentials` and
 * `CompositeChannelCredentials` into its TLS and plaintext protocol negotiators and reports the
 * negotiated `SecurityLevel` to call credentials and server calls.
 *
 * A utility company runs one dispatch server on a TLS port, where depot terminals sign in with a
 * client certificate and mobile technicians with a bearer token, and on a plaintext legacy port.
 * Keys and certificates reach the credentials as PEM streams. The technicians' call credentials
 * only release their token once the transport reports privacy and integrity, so pointing a
 * phone at the legacy port fails on the device and the token never leaves it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkOrderCredentialsTest {

    private static final String SERVICE = "utility.Dispatch";

    private static final String AUTHORITY = "dispatch.internal";

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final Context.Key<String> CALLER = Context.key("caller");

    private static final MethodDescriptor<StringValue, StringValue> SUBMIT =
            MethodDescriptor.<StringValue, StringValue>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE, "Submit"))
                    .setRequestMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .setResponseMarshaller(ProtoUtils.marshaller(StringValue.getDefaultInstance()))
                    .build();

    private final Dispatch dispatch = new Dispatch();

    private final CallerInterceptor callers =
            new CallerInterceptor(Map.of("tk-7f3a", "tech-ana", "tk-91c2", "tech-luis"));

    private CertificateAuthority operations;

    private Server tlsServer;

    private Server legacyServer;

    @BeforeAll
    void startDispatchServers() throws Exception {
        operations = new CertificateAuthority("CN=Grid Operations CA,O=Example Utility");
        Issued serverIdentity = operations.issue("CN=" + AUTHORITY + ",O=Example Utility", AUTHORITY);

        ServerCredentials tls = TlsServerCredentials.newBuilder()
                .keyManager(serverIdentity.certificatePem(), serverIdentity.keyPem())
                .trustManager(operations.certificatePem())
                .clientAuth(TlsServerCredentials.ClientAuth.OPTIONAL)
                .build();
        ServerServiceDefinition service = ServerInterceptors.intercept(dispatch.bindService(), callers);
        tlsServer = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0), tls)
                .addService(service)
                .build()
                .start();
        legacyServer = NettyServerBuilder.forAddress(
                        new InetSocketAddress("127.0.0.1", 0), InsecureServerCredentials.create())
                .addService(service)
                .build()
                .start();
    }

    @AfterAll
    void stopDispatchServers() throws Exception {
        tlsServer.shutdown();
        legacyServer.shutdown();
        assertThat(tlsServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(legacyServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void mobileTechnicianSignsInWithABearerTokenOverTls() throws Exception {
        BearerToken token = new BearerToken("tk-7f3a");
        ChannelCredentials credentials = CompositeChannelCredentials.create(
                TlsChannelCredentials.newBuilder().trustManager(operations.certificatePem()).build(), token);
        ManagedChannel phone = NettyChannelBuilder.forAddress("127.0.0.1", tlsServer.getPort(), credentials)
                .overrideAuthority(AUTHORITY)
                .build();
        try {
            assertThat(submit(phone, "WO-1001").getValue())
                    .isEqualTo("WO-1001 accepted for tech-ana (bearer token, PRIVACY_AND_INTEGRITY)");
            assertThat(submit(phone, "WO-1002").getValue())
                    .isEqualTo("WO-1002 accepted for tech-ana (bearer token, PRIVACY_AND_INTEGRITY)");
            assertThat(token.released()).containsExactly(
                    "dispatch.internal Submit at PRIVACY_AND_INTEGRITY",
                    "dispatch.internal Submit at PRIVACY_AND_INTEGRITY");
        } finally {
            close(phone);
        }
    }

    @Test
    void depotTerminalIsIdentifiedByItsClientCertificate() throws Exception {
        Issued terminal = operations.issue("CN=depot-north,O=Example Utility", null);
        ChannelCredentials credentials = TlsChannelCredentials.newBuilder()
                .keyManager(terminal.certificatePem(), terminal.keyPem())
                .trustManager(operations.certificatePem())
                .build();
        ManagedChannel depot = Grpc.newChannelBuilderForAddress("127.0.0.1", tlsServer.getPort(), credentials)
                .overrideAuthority(AUTHORITY)
                .build();
        try {
            assertThat(submit(depot, "WO-2001").getValue())
                    .isEqualTo("WO-2001 accepted for depot-north (client certificate, PRIVACY_AND_INTEGRITY)");
        } finally {
            close(depot);
        }
    }

    @Test
    void tokenIsWithheldFromThePlaintextLegacyPort() throws Exception {
        BearerToken token = new BearerToken("tk-91c2");
        ManagedChannel misconfiguredPhone = NettyChannelBuilder.forAddress("127.0.0.1", legacyServer.getPort(),
                        CompositeChannelCredentials.create(InsecureChannelCredentials.create(), token))
                .build();
        int rejectedBefore = callers.rejected();
        long acceptedBefore = dispatch.accepted();
        try {
            assertThatThrownBy(() -> submit(misconfiguredPhone, "WO-3001"))
                    .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                        assertThat(e.getStatus().getDescription())
                                .isEqualTo("token withheld from a NONE transport");
                    });
            assertThat(token.released()).isEmpty();
            assertThat(callers.rejected()).isEqualTo(rejectedBefore);
            assertThat(dispatch.accepted()).isEqualTo(acceptedBefore);
        } finally {
            close(misconfiguredPhone);
        }
    }

    @Test
    void anonymousCallersAreRejectedWithTheTransportTheyUsed() throws Exception {
        ManagedChannel legacyScript = NettyChannelBuilder.forAddress(
                        "127.0.0.1", legacyServer.getPort(), InsecureChannelCredentials.create())
                .build();
        ManagedChannel tlsScript = NettyChannelBuilder.forAddress("127.0.0.1", tlsServer.getPort(),
                        TlsChannelCredentials.newBuilder().trustManager(operations.certificatePem()).build())
                .overrideAuthority(AUTHORITY)
                .build();
        int rejectedBefore = callers.rejected();
        long acceptedBefore = dispatch.accepted();
        try {
            assertUnauthenticated(legacyScript, "anonymous caller on a NONE transport");
            assertUnauthenticated(tlsScript, "anonymous caller on a PRIVACY_AND_INTEGRITY transport");
            assertThat(callers.rejected()).isEqualTo(rejectedBefore + 2);
            assertThat(dispatch.accepted()).isEqualTo(acceptedBefore);
        } finally {
            close(legacyScript);
            close(tlsScript);
        }
    }

    private static void assertUnauthenticated(ManagedChannel channel, String description) {
        assertThatThrownBy(() -> submit(channel, "WO-4001"))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
                    assertThat(e.getStatus().getDescription()).isEqualTo(description);
                });
    }

    private static StringValue submit(ManagedChannel channel, String workOrder) {
        return ClientCalls.blockingUnaryCall(channel, SUBMIT,
                CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS), StringValue.of(workOrder));
    }

    private static void close(ManagedChannel channel) throws InterruptedException {
        channel.shutdownNow();
        assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private static InputStream pem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        String pem = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        return new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII));
    }

    /** A technician's token, released only to transports that report privacy and integrity. */
    private static final class BearerToken extends CallCredentials {

        private final String token;

        private final List<String> released = new CopyOnWriteArrayList<>();

        BearerToken(String token) {
            this.token = token;
        }

        List<String> released() {
            return released;
        }

        @Override
        public void applyRequestMetadata(RequestInfo info, Executor appExecutor, MetadataApplier applier) {
            SecurityLevel level = info.getSecurityLevel();
            if (level != SecurityLevel.PRIVACY_AND_INTEGRITY) {
                applier.fail(Status.UNAUTHENTICATED.withDescription("token withheld from a " + level + " transport"));
                return;
            }
            released.add(info.getAuthority() + " " + info.getMethodDescriptor().getBareMethodName() + " at " + level);
            Metadata headers = new Metadata();
            headers.put(AUTHORIZATION, "Bearer " + token);
            applier.apply(headers);
        }

        @Override
        public void thisUsesUnstableApi() {
        }
    }

    /** Names the caller from its client certificate or bearer token, together with the call's security level. */
    private static final class CallerInterceptor implements ServerInterceptor {

        private final Map<String, String> technicians;

        private final AtomicInteger rejected = new AtomicInteger();

        CallerInterceptor(Map<String, String> technicians) {
            this.technicians = technicians;
        }

        int rejected() {
            return rejected.get();
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            SecurityLevel level = call.getSecurityLevel();
            String caller = certificateHolder(call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION));
            if (caller != null) {
                caller = caller + " (client certificate, " + level + ")";
            } else {
                String authorization = headers.get(AUTHORIZATION);
                String technician = authorization == null ? null
                        : technicians.get(authorization.substring("Bearer ".length()));
                if (technician != null) {
                    caller = technician + " (bearer token, " + level + ")";
                }
            }
            if (caller == null) {
                rejected.incrementAndGet();
                call.close(Status.UNAUTHENTICATED.withDescription("anonymous caller on a " + level + " transport"),
                        new Metadata());
                return new ServerCall.Listener<ReqT>() {
                };
            }
            return Contexts.interceptCall(Context.current().withValue(CALLER, caller), call, headers, next);
        }

        private static String certificateHolder(SSLSession session) {
            if (session == null) {
                return null;
            }
            try {
                X500Name subject = new X500Name(session.getPeerPrincipal().getName());
                return subject.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString();
            } catch (SSLPeerUnverifiedException e) {
                return null;
            }
        }
    }

    /** Accepts work orders on behalf of the caller the interceptor identified. */
    private static final class Dispatch {

        private final AtomicLong accepted = new AtomicLong();

        long accepted() {
            return accepted.get();
        }

        ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder(SERVICE)
                    .addMethod(SUBMIT, ServerCalls.asyncUnaryCall(this::submit))
                    .build();
        }

        private void submit(StringValue workOrder, StreamObserver<StringValue> response) {
            accepted.incrementAndGet();
            response.onNext(StringValue.of(workOrder.getValue() + " accepted for " + CALLER.get()));
            response.onCompleted();
        }
    }

    /** A private key with the certificate the operations authority issued for it, both as PEM. */
    private record Issued(PrivateKey key, X509Certificate certificate) {

        InputStream keyPem() {
            return pem("PRIVATE KEY", key.getEncoded());
        }

        InputStream certificatePem() throws Exception {
            return pem("CERTIFICATE", certificate.getEncoded());
        }
    }

    /** A self-signed root that issues the server and depot-terminal certificates with BouncyCastle. */
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

        InputStream certificatePem() throws Exception {
            return pem("CERTIFICATE", certificate.getEncoded());
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
}
