/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Attributes;
import io.grpc.BindableService;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ChannelLogger;
import io.grpc.ClientCall;
import io.grpc.ClientStreamTracer;
import io.grpc.ConnectivityState;
import io.grpc.EquivalentAddressGroup;
import io.grpc.LoadBalancer;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.NameResolver;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.SynchronizationContext;
import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import io.grpc.util.CertificateUtils;
import io.grpc.util.ForwardingClientStreamTracer;
import io.grpc.util.ForwardingLoadBalancer;
import io.grpc.util.ForwardingLoadBalancerHelper;
import io.grpc.util.ForwardingSubchannel;
import io.grpc.util.GracefulSwitchLoadBalancer;
import io.grpc.util.MutableHandlerRegistry;
import io.grpc.util.OutlierDetectionLoadBalancerProvider;
import io.grpc.util.TransmitStatusRuntimeExceptionInterceptor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;

public class Grpc_utilTest {
    private static final String SERVICE_NAME = "grpc.util.TestService";
    private static final String UNARY_METHOD_NAME = "Unary";
    private static final MethodDescriptor<String, String> UNARY_METHOD = MethodDescriptor
            .<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(SERVICE_NAME, UNARY_METHOD_NAME))
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();
    private static final Metadata.Key<String> ERROR_TRAILER = Metadata.Key.of(
            "test-error",
            Metadata.ASCII_STRING_MARSHALLER);
    private static final String CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDEzCCAfugAwIBAgIUIrz8y6EBA6X6EPBPgsOFJCqkyHAwDQYJKoZIhvcNAQEL
            BQAwGTEXMBUGA1UEAwwOZ3JwYy11dGlsLXRlc3QwHhcNMjYwNTA3MTgyNTAxWhcN
            MjcwNTA3MTgyNTAxWjAZMRcwFQYDVQQDDA5ncnBjLXV0aWwtdGVzdDCCASIwDQYJ
            KoZIhvcNAQEBBQADggEPADCCAQoCggEBAOWVIecc1uie9eTmfx8ruvsMTQKOrMDJ
            STywhDlnNDKQOBSAdTgCBJ6RJVB9SOyc6Sz3N5NFAyZwesA8KmrpBRiHe8Yga4jA
            66yDtEALTxUzdstDsic6kxqQgsvymHxZOqA5/6YAc3S/IxQo/zuUNm63t91VikAy
            u4IudOaDLD4i8kYegxtI6UQTsfhzH/JJxM11BnU00nhS4/sv5/K0sEu4t2arueW7
            FyqnHakgm6MBGfeQV6nlqIPMzp2Q9GFTCT6fZSrgjLjpk0x6Y4UscyViEXEd9hmP
            OAtsJSa6o2K20VS31NQXpK0vVE+5XA/+YOuB+NV1cSNHoNwG7iPY6ZkCAwEAAaNT
            MFEwHQYDVR0OBBYEFITxxJB/b2erUCf5F+pz+X5sOokpMB8GA1UdIwQYMBaAFITx
            xJB/b2erUCf5F+pz+X5sOokpMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQEL
            BQADggEBACweKv+pRQwBLExFOPjUCo1VkcBwFWPg4PQGPl2+xhBDobDi2AIlZoQX
            Po5/twHGTE9VK6ZQEALwXgRbG09IGsDFwiDIqyKtmfR++BGqm8MXbev6TMD/Yu2E
            JfshTmTKDDIZJ1CBeXZZySZfi/rD02LuPWnb0u+1b8LuAmY+Myys+RuBOD3U30tJ
            F67CYksKN4vFcQqfdSrNXHAb7Q7Q4sZEnLG5e3W5aW2nwH2vKeKsLnGXHGKbj1/G
            iP54KFETHg8zc1l4EDYdLRjpzwCskzR520P11GyhagbRs+gGA1cO64UePGJVEFpy
            QRQR+T/MoWTlJKsXVfFEYo635NbDHEY=
            -----END CERTIFICATE-----
            """;
    private static final String PRIVATE_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDllSHnHNbonvXk
            5n8fK7r7DE0CjqzAyUk8sIQ5ZzQykDgUgHU4AgSekSVQfUjsnOks9zeTRQMmcHrA
            PCpq6QUYh3vGIGuIwOusg7RAC08VM3bLQ7InOpMakILL8ph8WTqgOf+mAHN0vyMU
            KP87lDZut7fdVYpAMruCLnTmgyw+IvJGHoMbSOlEE7H4cx/yScTNdQZ1NNJ4UuP7
            L+fytLBLuLdmq7nluxcqpx2pIJujARn3kFep5aiDzM6dkPRhUwk+n2Uq4Iy46ZNM
            emOFLHMlYhFxHfYZjzgLbCUmuqNittFUt9TUF6StL1RPuVwP/mDrgfjVdXEjR6Dc
            Bu4j2OmZAgMBAAECggEAB1dEdKA9khsuGTinuS05PviCrbRL8Ln8RrhisUwGtV9a
            6bM9/Ebak2A9qWmTd2cLhinCpp766y5cIHGJY4PUqxcpms5+fb/kcUQAqMPBI+Ld
            F4IFlunxWkoODUZ81Xdhb7jhtFLt835T7pu30IsBpA/pYpvZo4nzvgWCrPxmAbe7
            FXw4sy4IENFbNgenx/GNR8JLZoh4zjhluK1zGiBRpdGITutqryOZaP1eMg4sZvTo
            /sQXTvkKNqIj6TsJWUvHHSsjz53ON+nz+9pNkg33SNIXo7U5zEpRPice8P4Oaaue
            L69H7AKyATi6rPLyCRQBzfdJUZpaKA8rMUnHAfA18QKBgQD5CQWJL6l0ktdeLJwH
            P22+O+5NfY82PkVe+f2tsJxdU/sdUAFQ8+T+WHz6Z29TvvS5T0nxeeYILcy8vJGt
            rwUGBqHXw7F1IiSUvUQ5a0DrrixdewmeHutejBMegkc/yWFnMO88sTnc3dflSXHf
            JLFv0CyF2VCw/T0GPY18CnnRkQKBgQDsANajjWAieAV/YH6gf1jUZbtBBKcSC7zt
            zAyXohmnoD54NYkXn1Nde0mGy0pvGti7xS6FsrJxEA3ByRmiL/XkcJ49zA6SI3iw
            n0BVdNTFMytS6oQKquYJxB9ZDwylNYL6niz4OTALxhwkAl7X9DfrIQ3gifhxz75f
            lAxeL3oTiQKBgFv9CR5zjJSS6RrQP5Ity8vJN21IQ+41ckpy7VGLOzb+HnPlaZcN
            gDizVvbn3ieBEcShlR6teFrtEANnZChTzfc72+xnTNWHlxuaDelnxMNJwvmEyTiv
            EyIJe8Z5OPhG2sPTP4ubq1P6XhVD29whIam7q7aFX1kBMdlsQybed8EBAoGAW6lf
            tBo+0mRnTje+Nrv/Vk9E0VXrRdkFQh7UbThqWm6klK/GVURubopp3k6q1EhLEXLe
            RNNI9xzTGwbuS83w5Q7QhxPwPNZGE+Nma0p7MTRUiVnIexQxUG259y8fLDOCcbBp
            qucbQBdr6ph5GcsixNILv8AWnK2hNSIdQ5+Q/HECgYEAroWjZPqa9ljD8ATMv+/I
            RkCHPWck6PaFpxKQk1h0A41PpzDZkVMxDqj1RwS0VIzCY9tqCGMTx2f6oPXmDBCy
            /rT4RsMT9a/iIlyXy5ciANmb1KPWDats49oyHs3BOY7Dhoe/ycBrJZxwcnArZaK3
            VIiR7/vfsho6k8MM1+59iRk=
            -----END PRIVATE KEY-----
            """;

    @Test
    void mutableHandlerRegistryAddsLooksUpAndRemovesServices() {
        ServerCallHandler<String, String> handler = (call, headers) -> new ServerCall.Listener<>() { };
        ServerMethodDefinition<String, String> methodDefinition = ServerMethodDefinition.create(UNARY_METHOD, handler);
        ServerServiceDefinition serviceDefinition = ServerServiceDefinition.builder(SERVICE_NAME)
                .addMethod(methodDefinition)
                .build();
        MutableHandlerRegistry registry = new MutableHandlerRegistry();

        assertThat(registry.addService(serviceDefinition)).isNull();
        assertThat(registry.lookupMethod(UNARY_METHOD.getFullMethodName(), "test-authority"))
                .isSameAs(methodDefinition);
        assertThat(registry.getServices()).containsExactly(serviceDefinition);

        assertThat(registry.addService((BindableService) () -> serviceDefinition)).isSameAs(serviceDefinition);
        assertThat(registry.getServices()).containsExactly(serviceDefinition);
        assertThat(registry.removeService(serviceDefinition)).isTrue();
        assertThat(registry.removeService(serviceDefinition)).isFalse();
        assertThat(registry.lookupMethod(UNARY_METHOD.getFullMethodName(), "test-authority")).isNull();
    }

    @Test
    void transmitStatusRuntimeExceptionInterceptorClosesCallWithStatusAndTrailers() {
        Metadata trailers = new Metadata();
        trailers.put(ERROR_TRAILER, "blocked");
        StatusRuntimeException failure = Status.PERMISSION_DENIED
                .withDescription("not allowed")
                .asRuntimeException(trailers);
        RecordingServerCall call = new RecordingServerCall();
        ServerCallHandler<String, String> handler = (serverCall, headers) -> new ServerCall.Listener<>() {
            @Override
            public void onHalfClose() {
                throw failure;
            }
        };

        ServerCall.Listener<String> listener = TransmitStatusRuntimeExceptionInterceptor.instance()
                .interceptCall(call, new Metadata(), handler);
        listener.onHalfClose();

        assertThat(call.closedStatus.getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(call.closedStatus.getDescription()).isEqualTo("not allowed");
        assertThat(call.closedTrailers.get(ERROR_TRAILER)).isEqualTo("blocked");
    }

    @Test
    void certificateUtilitiesAndAdvancedTlsManagersLoadPemCredentials() throws Exception {
        X509Certificate[] certificates = CertificateUtils.getX509Certificates(inputStream(CERTIFICATE_PEM));
        PrivateKey privateKey = CertificateUtils.getPrivateKey(inputStream(PRIVATE_KEY_PEM));

        assertThat(certificates).hasSize(1);
        assertThat(certificates[0].getSubjectX500Principal().getName()).contains("CN=grpc-util-test");
        assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");

        AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();
        keyManager.updateIdentityCredentials(privateKey, certificates);
        assertThat(keyManager.chooseClientAlias(new String[] {"RSA"}, null, null)).isEqualTo("default");
        assertThat(keyManager.chooseServerAlias("RSA", null, null)).isEqualTo("default");
        assertThat(keyManager.getPrivateKey("default")).isSameAs(privateKey);
        assertThat(keyManager.getCertificateChain("default")).containsExactly(certificates);
        assertThat(keyManager.getCertificateChain("missing")).isNull();

        AdvancedTlsX509TrustManager trustManager = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_ONLY_VERIFICATION)
                .build();
        assertThat(trustManager.getAcceptedIssuers()).isEmpty();
        trustManager.updateTrustCredentials(certificates);
        assertThat(trustManager.getAcceptedIssuers()).hasSize(1);
        assertThatThrownBy(() -> trustManager.checkServerTrusted(certificates, "RSA"))
                .isInstanceOf(CertificateException.class)
                .hasMessageContaining("SSLEngine or Socket required");
    }

    @Test
    void advancedTlsManagersLoadCredentialsFromFiles() throws Exception {
        Path certificateFile = Files.createTempFile("grpc-util-cert", ".pem");
        Path privateKeyFile = Files.createTempFile("grpc-util-key", ".pem");
        try {
            Files.writeString(certificateFile, CERTIFICATE_PEM, StandardCharsets.US_ASCII);
            Files.writeString(privateKeyFile, PRIVATE_KEY_PEM, StandardCharsets.US_ASCII);

            AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();
            keyManager.updateIdentityCredentialsFromFile(privateKeyFile.toFile(), certificateFile.toFile());
            assertThat(keyManager.getCertificateChain("default")).hasSize(1);
            assertThat(keyManager.getPrivateKey("default").getAlgorithm()).isEqualTo("RSA");

            AdvancedTlsX509TrustManager trustManager = AdvancedTlsX509TrustManager.newBuilder().build();
            trustManager.updateTrustCredentialsFromFile(certificateFile.toFile());
            assertThat(trustManager.getAcceptedIssuers()).hasSize(1);
        } finally {
            Files.deleteIfExists(certificateFile);
            Files.deleteIfExists(privateKeyFile);
        }
    }

    @Test
    void forwardingClientStreamTracerDelegatesEveryCallback() {
        RecordingClientStreamTracer delegate = new RecordingClientStreamTracer();
        ForwardingClientStreamTracer forwardingTracer = new ForwardingClientStreamTracer() {
            @Override
            protected ClientStreamTracer delegate() {
                return delegate;
            }
        };

        Metadata metadata = new Metadata();
        forwardingTracer.streamCreated(Attributes.EMPTY, metadata);
        forwardingTracer.createPendingStream();
        forwardingTracer.outboundHeaders();
        forwardingTracer.inboundHeaders();
        forwardingTracer.inboundHeaders(metadata);
        forwardingTracer.inboundTrailers(metadata);
        forwardingTracer.addOptionalLabel("label", "value");
        forwardingTracer.outboundMessage(1);
        forwardingTracer.inboundMessage(2);
        forwardingTracer.outboundMessageSent(3, 4L, 5L);
        forwardingTracer.inboundMessageRead(6, 7L, 8L);
        forwardingTracer.outboundWireSize(9L);
        forwardingTracer.outboundUncompressedSize(10L);
        forwardingTracer.inboundWireSize(11L);
        forwardingTracer.inboundUncompressedSize(12L);
        forwardingTracer.streamClosed(Status.OK);

        assertThat(delegate.events).containsExactly(
                "streamCreated",
                "createPendingStream",
                "outboundHeaders",
                "inboundHeaders",
                "inboundHeadersWithMetadata",
                "inboundTrailers",
                "addOptionalLabel:label=value",
                "outboundMessage:1",
                "inboundMessage:2",
                "outboundMessageSent:3:4:5",
                "inboundMessageRead:6:7:8",
                "outboundWireSize:9",
                "outboundUncompressedSize:10",
                "inboundWireSize:11",
                "inboundUncompressedSize:12",
                "streamClosed:OK");
        assertThat(forwardingTracer.toString()).contains("delegate=").contains(delegate.toString());
    }

    @Test
    void forwardingLoadBalancerAndSubchannelDelegateCalls() {
        RecordingLoadBalancer delegateLoadBalancer = new RecordingLoadBalancer();
        ForwardingLoadBalancer forwardingLoadBalancer = new ForwardingLoadBalancer() {
            @Override
            protected LoadBalancer delegate() {
                return delegateLoadBalancer;
            }
        };
        EquivalentAddressGroup addressGroup = new EquivalentAddressGroup(new InetSocketAddress("localhost", 8080));
        LoadBalancer.ResolvedAddresses resolvedAddresses = LoadBalancer.ResolvedAddresses.newBuilder()
                .setAddresses(List.of(addressGroup))
                .setAttributes(Attributes.EMPTY)
                .build();

        forwardingLoadBalancer.handleResolvedAddresses(resolvedAddresses);
        forwardingLoadBalancer.handleNameResolutionError(Status.UNAVAILABLE.withDescription("resolver failed"));
        forwardingLoadBalancer.requestConnection();
        assertThat(forwardingLoadBalancer.canHandleEmptyAddressListFromNameResolution()).isTrue();
        forwardingLoadBalancer.shutdown();

        assertThat(delegateLoadBalancer.events).containsExactly(
                "resolved:1",
                "error:UNAVAILABLE",
                "requestConnection",
                "canHandleEmptyAddressList",
                "shutdown");

        RecordingSubchannel delegateSubchannel = new RecordingSubchannel(List.of(addressGroup));
        ForwardingSubchannel forwardingSubchannel = new ForwardingSubchannel() {
            @Override
            protected LoadBalancer.Subchannel delegate() {
                return delegateSubchannel;
            }
        };
        forwardingSubchannel.start(stateInfo -> delegateSubchannel.events.add("listener:" + stateInfo.getState()));
        forwardingSubchannel.requestConnection();
        forwardingSubchannel.updateAddresses(List.of(addressGroup));
        assertThat(forwardingSubchannel.getAllAddresses()).containsExactly(addressGroup);
        assertThat(forwardingSubchannel.getAttributes()).isSameAs(Attributes.EMPTY);
        assertThat(forwardingSubchannel.asChannel().authority()).isEqualTo("subchannel-authority");
        assertThat(forwardingSubchannel.getConnectedAddressAttributes()).isSameAs(Attributes.EMPTY);
        forwardingSubchannel.shutdown();

        assertThat(delegateSubchannel.events).containsExactly(
                "start",
                "requestConnection",
                "updateAddresses:1",
                "shutdown");
    }

    @Test
    void forwardingLoadBalancerHelperDelegatesSupportedOperations() {
        EquivalentAddressGroup addressGroup = new EquivalentAddressGroup(new InetSocketAddress("localhost", 9090));
        try (RecordingHelper delegate = new RecordingHelper(new RecordingSubchannel(List.of(addressGroup)))) {
            ForwardingLoadBalancerHelper forwardingHelper = new ForwardingLoadBalancerHelper() {
                @Override
                protected LoadBalancer.Helper delegate() {
                    return delegate;
                }
            };
            LoadBalancer.CreateSubchannelArgs createArgs = LoadBalancer.CreateSubchannelArgs.newBuilder()
                    .setAddresses(List.of(addressGroup))
                    .setAttributes(Attributes.EMPTY)
                    .build();
            LoadBalancer.SubchannelPicker picker = new LoadBalancer.SubchannelPicker() {
                @Override
                public LoadBalancer.PickResult pickSubchannel(LoadBalancer.PickSubchannelArgs args) {
                    return LoadBalancer.PickResult.withNoResult();
                }
            };

            assertThat(forwardingHelper.createSubchannel(createArgs)).isSameAs(delegate.subchannel);
            forwardingHelper.updateBalancingState(ConnectivityState.READY, picker);
            forwardingHelper.refreshNameResolution();
            forwardingHelper.ignoreRefreshNameResolutionCheck();
            assertThat(forwardingHelper.getAuthority()).isEqualTo("recording-authority");
            assertThat(forwardingHelper.getSynchronizationContext()).isSameAs(delegate.synchronizationContext);
            assertThat(forwardingHelper.getScheduledExecutorService()).isSameAs(delegate.executor);
            assertThat(forwardingHelper.getChannelLogger()).isSameAs(delegate.channelLogger);
            forwardingHelper.getChannelLogger().log(ChannelLogger.ChannelLogLevel.INFO, "helper log");

            assertThat(delegate.events).containsExactly(
                    "createSubchannel:1",
                    "updateBalancingState:READY",
                    "refreshNameResolution",
                    "ignoreRefreshNameResolutionCheck",
                    "log:INFO:helper log");
        }
    }

    @Test
    void gracefulSwitchLoadBalancerKeepsCurrentPolicyUntilPendingPolicyIsReady() {
        EquivalentAddressGroup addressGroup = new EquivalentAddressGroup(new InetSocketAddress("localhost", 10080));
        try (RecordingHelper helper = new RecordingHelper(new RecordingSubchannel(List.of(addressGroup)))) {
            GracefulSwitchLoadBalancer loadBalancer = new GracefulSwitchLoadBalancer(helper);
            SwitchableLoadBalancerFactory firstFactory = new SwitchableLoadBalancerFactory();
            SwitchableLoadBalancerFactory secondFactory = new SwitchableLoadBalancerFactory();

            loadBalancer.handleResolvedAddresses(LoadBalancer.ResolvedAddresses.newBuilder()
                    .setAddresses(List.of(addressGroup))
                    .setAttributes(Attributes.EMPTY)
                    .setLoadBalancingPolicyConfig(GracefulSwitchLoadBalancer.createLoadBalancingPolicyConfig(
                            firstFactory,
                            "first-policy-config"))
                    .build());
            SwitchableLoadBalancer firstLoadBalancer = firstFactory.loadBalancer;
            assertThat(firstLoadBalancer).isNotNull();
            assertThat(helper.events).containsExactly("updateBalancingState:CONNECTING");

            helper.events.clear();
            firstLoadBalancer.updateState(ConnectivityState.READY);
            assertThat(helper.events).containsExactly("updateBalancingState:READY");

            helper.events.clear();
            loadBalancer.handleResolvedAddresses(LoadBalancer.ResolvedAddresses.newBuilder()
                    .setAddresses(List.of(addressGroup))
                    .setAttributes(Attributes.EMPTY)
                    .setLoadBalancingPolicyConfig(GracefulSwitchLoadBalancer.createLoadBalancingPolicyConfig(
                            secondFactory,
                            "second-policy-config"))
                    .build());
            SwitchableLoadBalancer secondLoadBalancer = secondFactory.loadBalancer;
            assertThat(secondLoadBalancer).isNotNull();
            secondLoadBalancer.updateState(ConnectivityState.CONNECTING);
            assertThat(helper.events).isEmpty();
            assertThat(firstLoadBalancer.events).isEmpty();

            secondLoadBalancer.updateState(ConnectivityState.READY);
            assertThat(helper.events).containsExactly("updateBalancingState:READY");
            assertThat(firstLoadBalancer.events).containsExactly("shutdown");

            loadBalancer.shutdown();
            assertThat(secondLoadBalancer.events).containsExactly("shutdown");
        }
    }

    @Test
    void outlierDetectionProviderParsesPolicyConfigAndCreatesLoadBalancer() {
        OutlierDetectionLoadBalancerProvider provider = new OutlierDetectionLoadBalancerProvider();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("interval", "10s");
        config.put("baseEjectionTime", "30s");
        config.put("maxEjectionTime", "300s");
        config.put("maxEjectionPercentage", 50D);
        config.put("successRateEjection", Map.of(
                "stdevFactor", 1900D,
                "enforcementPercentage", 100D,
                "minimumHosts", 5D,
                "requestVolume", 100D));
        config.put("failurePercentageEjection", Map.of(
                "threshold", 85D,
                "enforcementPercentage", 100D,
                "minimumHosts", 5D,
                "requestVolume", 50D));
        config.put("childPolicy", List.of(Map.of("pick_first", Map.of())));

        NameResolver.ConfigOrError parsed = provider.parseLoadBalancingPolicyConfig(config);

        assertThat(provider.isAvailable()).isTrue();
        assertThat(provider.getPriority()).isPositive();
        assertThat(provider.getPolicyName()).isEqualTo("outlier_detection_experimental");
        assertThat(parsed.getError()).isNull();
        Object parsedConfig = parsed.getConfig();
        assertThat(parsedConfig).isNotNull();
        assertThat(String.valueOf(parsedConfig)).contains("OutlierDetectionLoadBalancerConfig");

        try (MinimalHelper helper = new MinimalHelper()) {
            LoadBalancer loadBalancer = provider.newLoadBalancer(helper);
            assertThat(loadBalancer).isNotNull();
            loadBalancer.shutdown();
        }
    }

    private static InputStream inputStream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static final class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public InputStream stream(String value) {
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String parse(InputStream stream) {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to parse string payload", exception);
            }
        }
    }

    private static final class RecordingServerCall extends ServerCall<String, String> {
        private Status closedStatus;
        private Metadata closedTrailers;

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
            closedStatus = status;
            closedTrailers = trailers;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return UNARY_METHOD;
        }
    }

    private static final class RecordingClientStreamTracer extends ClientStreamTracer {
        private final List<String> events = new ArrayList<>();

        @Override
        public void streamCreated(Attributes transportAttrs, Metadata headers) {
            events.add("streamCreated");
        }

        @Override
        public void createPendingStream() {
            events.add("createPendingStream");
        }

        @Override
        public void outboundHeaders() {
            events.add("outboundHeaders");
        }

        @Override
        public void inboundHeaders() {
            events.add("inboundHeaders");
        }

        @Override
        public void inboundHeaders(Metadata headers) {
            events.add("inboundHeadersWithMetadata");
        }

        @Override
        public void inboundTrailers(Metadata trailers) {
            events.add("inboundTrailers");
        }

        @Override
        public void addOptionalLabel(String key, String value) {
            events.add("addOptionalLabel:" + key + "=" + value);
        }

        @Override
        public void streamClosed(Status status) {
            events.add("streamClosed:" + status.getCode());
        }

        @Override
        public void outboundMessage(int seqNo) {
            events.add("outboundMessage:" + seqNo);
        }

        @Override
        public void inboundMessage(int seqNo) {
            events.add("inboundMessage:" + seqNo);
        }

        @Override
        public void outboundMessageSent(int seqNo, long optionalWireSize, long optionalUncompressedSize) {
            events.add("outboundMessageSent:" + seqNo + ":" + optionalWireSize + ":" + optionalUncompressedSize);
        }

        @Override
        public void inboundMessageRead(int seqNo, long optionalWireSize, long optionalUncompressedSize) {
            events.add("inboundMessageRead:" + seqNo + ":" + optionalWireSize + ":" + optionalUncompressedSize);
        }

        @Override
        public void outboundWireSize(long bytes) {
            events.add("outboundWireSize:" + bytes);
        }

        @Override
        public void outboundUncompressedSize(long bytes) {
            events.add("outboundUncompressedSize:" + bytes);
        }

        @Override
        public void inboundWireSize(long bytes) {
            events.add("inboundWireSize:" + bytes);
        }

        @Override
        public void inboundUncompressedSize(long bytes) {
            events.add("inboundUncompressedSize:" + bytes);
        }
    }

    private static final class RecordingLoadBalancer extends LoadBalancer {
        private final List<String> events = new ArrayList<>();

        @Override
        public void handleResolvedAddresses(ResolvedAddresses resolvedAddresses) {
            events.add("resolved:" + resolvedAddresses.getAddresses().size());
        }

        @Override
        public void handleNameResolutionError(Status error) {
            events.add("error:" + error.getCode());
        }

        @Override
        public void shutdown() {
            events.add("shutdown");
        }

        @Override
        public boolean canHandleEmptyAddressListFromNameResolution() {
            events.add("canHandleEmptyAddressList");
            return true;
        }

        @Override
        public void requestConnection() {
            events.add("requestConnection");
        }
    }

    private static final class RecordingSubchannel extends LoadBalancer.Subchannel {
        private final List<String> events = new ArrayList<>();
        private final List<EquivalentAddressGroup> addresses;

        private RecordingSubchannel(List<EquivalentAddressGroup> addresses) {
            this.addresses = addresses;
        }

        @Override
        public void start(LoadBalancer.SubchannelStateListener listener) {
            events.add("start");
        }

        @Override
        public void shutdown() {
            events.add("shutdown");
        }

        @Override
        public void requestConnection() {
            events.add("requestConnection");
        }

        @Override
        public List<EquivalentAddressGroup> getAllAddresses() {
            return addresses;
        }

        @Override
        public Attributes getAttributes() {
            return Attributes.EMPTY;
        }

        @Override
        public Channel asChannel() {
            return new Channel() {
                @Override
                public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(
                        MethodDescriptor<RequestT, ResponseT> methodDescriptor,
                        CallOptions callOptions) {
                    throw new UnsupportedOperationException("No calls are made by this test channel");
                }

                @Override
                public String authority() {
                    return "subchannel-authority";
                }
            };
        }

        @Override
        public void updateAddresses(List<EquivalentAddressGroup> addressGroups) {
            events.add("updateAddresses:" + addressGroups.size());
        }

        @Override
        public Attributes getConnectedAddressAttributes() {
            return Attributes.EMPTY;
        }
    }

    private static final class SwitchableLoadBalancerFactory extends LoadBalancer.Factory {
        private SwitchableLoadBalancer loadBalancer;

        @Override
        public LoadBalancer newLoadBalancer(LoadBalancer.Helper helper) {
            loadBalancer = new SwitchableLoadBalancer(helper);
            return loadBalancer;
        }
    }

    private static final class SwitchableLoadBalancer extends LoadBalancer {
        private final LoadBalancer.Helper helper;
        private final List<String> events = new ArrayList<>();

        private SwitchableLoadBalancer(LoadBalancer.Helper helper) {
            this.helper = helper;
        }

        private void updateState(ConnectivityState state) {
            helper.updateBalancingState(state, LoadBalancer.EMPTY_PICKER);
        }

        @Override
        public void handleNameResolutionError(Status error) {
            events.add("error:" + error.getCode());
        }

        @Override
        public void shutdown() {
            events.add("shutdown");
        }
    }

    private static final class RecordingHelper extends LoadBalancer.Helper implements AutoCloseable {
        private final List<String> events = new ArrayList<>();
        private final RecordingSubchannel subchannel;
        private final SynchronizationContext synchronizationContext = new SynchronizationContext(
                (thread, throwable) -> { });
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        private final ChannelLogger channelLogger = new ChannelLogger() {
            @Override
            public void log(ChannelLogger.ChannelLogLevel level, String message) {
                events.add("log:" + level + ":" + message);
            }

            @Override
            public void log(ChannelLogger.ChannelLogLevel level, String messageFormat, Object... args) {
                events.add("log:" + level + ":" + messageFormat.formatted(args));
            }
        };

        private RecordingHelper(RecordingSubchannel subchannel) {
            this.subchannel = subchannel;
        }

        @Override
        public LoadBalancer.Subchannel createSubchannel(LoadBalancer.CreateSubchannelArgs args) {
            events.add("createSubchannel:" + args.getAddresses().size());
            return subchannel;
        }

        @Override
        public ManagedChannel createOobChannel(EquivalentAddressGroup eag, String authority) {
            throw new UnsupportedOperationException("No out-of-band channels are created by this test");
        }

        @Override
        public void updateBalancingState(ConnectivityState newState, LoadBalancer.SubchannelPicker newPicker) {
            events.add("updateBalancingState:" + newState);
        }

        @Override
        public void refreshNameResolution() {
            events.add("refreshNameResolution");
        }

        @Override
        public void ignoreRefreshNameResolutionCheck() {
            events.add("ignoreRefreshNameResolutionCheck");
        }

        @Override
        public String getAuthority() {
            return "recording-authority";
        }

        @Override
        public SynchronizationContext getSynchronizationContext() {
            return synchronizationContext;
        }

        @Override
        public ScheduledExecutorService getScheduledExecutorService() {
            return executor;
        }

        @Override
        public ChannelLogger getChannelLogger() {
            return channelLogger;
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }

    private static final class MinimalHelper extends LoadBalancer.Helper implements AutoCloseable {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        @Override
        public ManagedChannel createOobChannel(EquivalentAddressGroup eag, String authority) {
            throw new UnsupportedOperationException("No out-of-band channels are created by this test");
        }

        @Override
        public void updateBalancingState(ConnectivityState newState, LoadBalancer.SubchannelPicker newPicker) {
        }

        @Override
        public String getAuthority() {
            return "test-authority";
        }

        @Override
        public SynchronizationContext getSynchronizationContext() {
            return new SynchronizationContext((thread, throwable) -> { });
        }

        @Override
        public ScheduledExecutorService getScheduledExecutorService() {
            return executor;
        }

        @Override
        public ChannelLogger getChannelLogger() {
            return new ChannelLogger() {
                @Override
                public void log(ChannelLogger.ChannelLogLevel level, String message) {
                }

                @Override
                public void log(ChannelLogger.ChannelLogLevel level, String messageFormat, Object... args) {
                }
            };
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }

}
