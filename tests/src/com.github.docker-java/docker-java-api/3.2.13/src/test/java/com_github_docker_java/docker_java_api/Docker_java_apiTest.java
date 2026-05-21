/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_docker_java.docker_java_api;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.BadRequestException;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.InternalServerErrorException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BindOptions;
import com.github.dockerjava.api.model.BindPropagation;
import com.github.dockerjava.api.model.Binds;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.ContainerDNSConfig;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ContainerSpec;
import com.github.dockerjava.api.model.ContainerSpecConfig;
import com.github.dockerjava.api.model.ContainerSpecFile;
import com.github.dockerjava.api.model.ContainerSpecSecret;
import com.github.dockerjava.api.model.Device;
import com.github.dockerjava.api.model.DeviceRequest;
import com.github.dockerjava.api.model.Driver;
import com.github.dockerjava.api.model.EndpointResolutionMode;
import com.github.dockerjava.api.model.EndpointSpec;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.ExposedPorts;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Links;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.LogConfig.LoggingType;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.NetworkAttachmentConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.PortConfig;
import com.github.dockerjava.api.model.PortConfig.PublishMode;
import com.github.dockerjava.api.model.PortConfigProtocol;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.PropagationMode;
import com.github.dockerjava.api.model.ResourceRequirements;
import com.github.dockerjava.api.model.ResourceSpecs;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.SELContext;
import com.github.dockerjava.api.model.ServiceGlobalModeOptions;
import com.github.dockerjava.api.model.ServiceMode;
import com.github.dockerjava.api.model.ServiceModeConfig;
import com.github.dockerjava.api.model.ServicePlacement;
import com.github.dockerjava.api.model.ServiceReplicatedModeOptions;
import com.github.dockerjava.api.model.ServiceRestartCondition;
import com.github.dockerjava.api.model.ServiceRestartPolicy;
import com.github.dockerjava.api.model.ServiceSpec;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.TaskSpec;
import com.github.dockerjava.api.model.Ulimit;
import com.github.dockerjava.api.model.UpdateConfig;
import com.github.dockerjava.api.model.UpdateFailureAction;
import com.github.dockerjava.api.model.UpdateOrder;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.VolumeOptions;
import com.github.dockerjava.api.model.Volumes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Docker_java_apiTest {
    @Test
    void parsesExposedPortsAndPortBindings() {
        ExposedPort defaultPort = ExposedPort.parse("8080");
        ExposedPort udpPort = ExposedPort.parse("5353/udp");
        ExposedPort sctpPort = ExposedPort.sctp(9899);

        assertThat(defaultPort.getPort()).isEqualTo(8080);
        assertThat(defaultPort.getProtocol()).isEqualTo(InternetProtocol.TCP);
        assertThat(defaultPort).isEqualTo(ExposedPort.tcp(8080));
        assertThat(defaultPort.toString()).isEqualTo("8080/tcp");
        assertThat(udpPort.getProtocol()).isEqualTo(InternetProtocol.UDP);
        assertThat(sctpPort.toString()).isEqualTo("9899/sctp");
        assertThat(InternetProtocol.parse("TCP")).isEqualTo(InternetProtocol.TCP);

        Ports.Binding loopbackBinding = Ports.Binding.parse("127.0.0.1:18080");
        Ports.Binding rangeBinding = Ports.Binding.bindPortRange(49153, 49155);
        Ports.Binding hostOnlyBinding = Ports.Binding.parse("127.0.0.1");

        assertThat(loopbackBinding.getHostIp()).isEqualTo("127.0.0.1");
        assertThat(loopbackBinding.getHostPortSpec()).isEqualTo("18080");
        assertThat(loopbackBinding.toString()).isEqualTo("127.0.0.1:18080");
        assertThat(rangeBinding.getHostPortSpec()).isEqualTo("49153-49155");
        assertThat(hostOnlyBinding.getHostIp()).isEqualTo("127.0.0.1");
        assertThat(hostOnlyBinding.getHostPortSpec()).isNull();

        PortBinding fullBinding = PortBinding.parse("127.0.0.1:18080:8080/tcp");
        PortBinding dynamicBinding = PortBinding.parse("5353/udp");

        assertThat(fullBinding.getBinding()).isEqualTo(loopbackBinding);
        assertThat(fullBinding.getExposedPort()).isEqualTo(defaultPort);
        assertThat(dynamicBinding.getBinding()).isEqualTo(Ports.Binding.empty());
        assertThat(dynamicBinding.getExposedPort()).isEqualTo(udpPort);

        assertThatThrownBy(() -> ExposedPort.parse("not-a-port/tcp")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortBinding.parse("too:many:parts:80/tcp"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsPortsExposedPortsAndVolumesToPrimitiveApiShapes() {
        Ports ports = new Ports();
        ports.bind(ExposedPort.tcp(8080), Ports.Binding.bindIpAndPort("127.0.0.1", 18080));
        ports.bind(ExposedPort.tcp(8080), Ports.Binding.bindPort(28080));
        ports.add(new PortBinding(Ports.Binding.bindPort(15353), ExposedPort.udp(5353)));

        Map<ExposedPort, Ports.Binding[]> bindings = ports.getBindings();
        assertThat(bindings.get(ExposedPort.tcp(8080))).containsExactly(
                Ports.Binding.bindIpAndPort("127.0.0.1", 18080),
                Ports.Binding.bindPort(28080));
        assertThat(bindings.get(ExposedPort.udp(5353))).containsExactly(Ports.Binding.bindPort(15353));

        Map<String, List<Map<String, String>>> primitivePorts = ports.toPrimitive();
        assertThat(primitivePorts.get("8080/tcp")).containsExactly(
                Map.of("HostIp", "127.0.0.1", "HostPort", "18080"),
                Map.of("HostIp", "", "HostPort", "28080"));
        assertThat(Ports.fromPrimitive(primitivePorts).getBindings())
                .containsKeys(ExposedPort.tcp(8080), ExposedPort.udp(5353));

        Map<String, List<Map<String, String>>> primitiveWithUnpublishedPort = new HashMap<>();
        primitiveWithUnpublishedPort.put("443/tcp", null);
        Ports unpublishedPort = Ports.fromPrimitive(primitiveWithUnpublishedPort);
        assertThat(unpublishedPort.getBindings().get(ExposedPort.tcp(443))).isNull();
        assertThat(unpublishedPort.toPrimitive()).containsEntry("443/tcp", null);

        ExposedPorts exposedPorts = new ExposedPorts(ExposedPort.tcp(80), ExposedPort.udp(53));
        assertThat(exposedPorts.toPrimitive()).containsKeys("80/tcp", "53/udp");
        assertThat(ExposedPorts.fromPrimitive(exposedPorts.toPrimitive()).getExposedPorts())
                .containsExactlyInAnyOrder(ExposedPort.tcp(80), ExposedPort.udp(53));

        Volumes volumes = new Volumes(new Volume("/data"), new Volume("/cache"));
        assertThat(volumes.toPrimitive()).containsKeys("/data", "/cache");
        assertThat(Volumes.fromPrimitive(volumes.toPrimitive()).getVolumes())
                .containsExactlyInAnyOrder(new Volume("/data"), new Volume("/cache"));
    }

    @Test
    void parsesMountsBindsLinksAndRestartPolicies() {
        Bind bind = Bind.parse("/srv/app:/app:ro,Z,nocopy,shared");

        assertThat(bind.getPath()).isEqualTo("/srv/app");
        assertThat(bind.getVolume()).isEqualTo(new Volume("/app"));
        assertThat(bind.getAccessMode()).isEqualTo(AccessMode.ro);
        assertThat(bind.getSecMode()).isEqualTo(SELContext.single);
        assertThat(bind.getNoCopy()).isTrue();
        assertThat(bind.getPropagationMode()).isEqualTo(PropagationMode.SHARED);
        assertThat(bind.toString()).isEqualTo("/srv/app:/app:ro,Z,nocopy,shared");
        assertThat(AccessMode.fromBoolean(true).toBoolean()).isTrue();
        assertThat(AccessMode.fromBoolean(false)).isEqualTo(AccessMode.ro);
        assertThat(SELContext.fromString("z")).isEqualTo(SELContext.shared);
        assertThat(PropagationMode.fromString("rslave")).isEqualTo(PropagationMode.RSLAVE);

        Binds binds = new Binds(bind, new Bind("/tmp", new Volume("/scratch")));
        assertThat(binds.toPrimitive()).containsExactly("/srv/app:/app:ro,Z,nocopy,shared", "/tmp:/scratch:rw");
        assertThat(Binds.fromPrimitive(binds.toPrimitive()).getBinds()).containsExactly(binds.getBinds());

        Link link = Link.parse("/database:/application/db");
        Links links = new Links(link, new Link("redis", "cache"));
        assertThat(link.getName()).isEqualTo("database");
        assertThat(link.getAlias()).isEqualTo("db");
        assertThat(links.toPrimitive()).containsExactly("database:db", "redis:cache");
        assertThat(Links.fromPrimitive(links.toPrimitive()).getLinks()).containsExactly(links.getLinks());

        RestartPolicy noRestart = RestartPolicy.parse("no");
        RestartPolicy onFailure = RestartPolicy.parse("on-failure:3");
        assertThat(noRestart).isEqualTo(RestartPolicy.noRestart());
        assertThat(noRestart.toString()).isEqualTo("no");
        assertThat(onFailure.getName()).isEqualTo("on-failure");
        assertThat(onFailure.getMaximumRetryCount()).isEqualTo(3);
        assertThat(onFailure.toString()).isEqualTo("on-failure:3");
        assertThat(RestartPolicy.parse("always")).isEqualTo(RestartPolicy.alwaysRestart());
        assertThat(RestartPolicy.parse("unless-stopped")).isEqualTo(RestartPolicy.unlessStoppedRestart());

        assertThatThrownBy(() -> Bind.parse("/only-one-part")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Link.parse("missing-alias")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestartPolicy.parse("sometimes")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsContainerAndHostConfigurationModels() {
        ContainerConfig containerConfig = new ContainerConfig()
                .withImage("busybox:latest")
                .withCmd(new String[] {"sh", "-c", "echo ok"})
                .withEntrypoint(new String[] {"/bin/sh"})
                .withEnv(new String[] {"APP_ENV=test", "LOG_LEVEL=debug"})
                .withLabels(Map.of("suite", "native-image", "component", "docker-java-api"))
                .withExposedPorts(new ExposedPorts(ExposedPort.tcp(8080), ExposedPort.udp(5353)))
                .withWorkingDir("/workspace")
                .withUser("1000:1000")
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withStdinOpen(false)
                .withTty(false);

        assertThat(containerConfig.getImage()).isEqualTo("busybox:latest");
        assertThat(containerConfig.getCmd()).containsExactly("sh", "-c", "echo ok");
        assertThat(containerConfig.getEntrypoint()).containsExactly("/bin/sh");
        assertThat(containerConfig.getEnv()).containsExactly("APP_ENV=test", "LOG_LEVEL=debug");
        assertThat(containerConfig.getLabels()).containsEntry("component", "docker-java-api");
        assertThat(containerConfig.getExposedPorts())
                .containsExactlyInAnyOrder(ExposedPort.tcp(8080), ExposedPort.udp(5353));
        assertThat(containerConfig.getWorkingDir()).isEqualTo("/workspace");
        assertThat(containerConfig.getAttachStdout()).isTrue();

        Mount mount = new Mount()
                .withType(MountType.BIND)
                .withSource("/srv/app")
                .withTarget("/app")
                .withReadOnly(true)
                .withBindOptions(new BindOptions().withPropagation(BindPropagation.R_PRIVATE))
                .withVolumeOptions(new VolumeOptions().withNoCopy(true).withLabels(Map.of("owner", "tests")));
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(new Bind("/srv/app", new Volume("/app"), AccessMode.ro))
                .withPortBindings(PortBinding.parse("127.0.0.1:18080:8080/tcp"))
                .withRestartPolicy(RestartPolicy.onFailureRestart(2))
                .withNetworkMode("custom-network")
                .withCapAdd(Capability.NET_ADMIN)
                .withCapDrop(Capability.MKNOD)
                .withMemory(128L * 1024L * 1024L)
                .withCpuShares(512)
                .withPrivileged(false)
                .withPublishAllPorts(false)
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of("/run", "rw,noexec,nosuid,size=65536k"))
                .withSysctls(Map.of("net.ipv4.ip_forward", "1"))
                .withSecurityOpts(List.of("no-new-privileges"))
                .withMounts(List.of(mount));

        assertThat(hostConfig.getBinds()).containsExactly(new Bind("/srv/app", new Volume("/app"), AccessMode.ro));
        assertThat(hostConfig.getPortBindings().getBindings().get(ExposedPort.tcp(8080)))
                .containsExactly(Ports.Binding.bindIpAndPort("127.0.0.1", 18080));
        assertThat(hostConfig.getRestartPolicy()).isEqualTo(RestartPolicy.onFailureRestart(2));
        assertThat(hostConfig.isUserDefinedNetwork()).isTrue();
        assertThat(hostConfig.getCapAdd()).containsExactly(Capability.NET_ADMIN);
        assertThat(hostConfig.getCapDrop()).containsExactly(Capability.MKNOD);
        assertThat(hostConfig.getMemory()).isEqualTo(128L * 1024L * 1024L);
        assertThat(hostConfig.getCpuShares()).isEqualTo(512);
        assertThat(hostConfig.getTmpFs()).containsEntry("/run", "rw,noexec,nosuid,size=65536k");
        assertThat(hostConfig.getSysctls()).containsEntry("net.ipv4.ip_forward", "1");
        assertThat(hostConfig.getSecurityOpts()).containsExactly("no-new-privileges");
        assertThat(hostConfig.getMounts()).containsExactly(mount);
    }

    @Test
    void configuresDeviceLoggingAndUlimitHostOptions() {
        Device defaultDevice = Device.parse("/dev/fuse");
        Device remappedReadOnlyDevice = Device.parse("/dev/sda:/dev/xvdc:r");
        Device modeOnlyDevice = Device.parse("/dev/snd:mr");

        assertThat(defaultDevice.getPathOnHost()).isEqualTo("/dev/fuse");
        assertThat(defaultDevice.getPathInContainer()).isEqualTo("/dev/fuse");
        assertThat(defaultDevice.getcGroupPermissions()).isEqualTo("rwm");
        assertThat(remappedReadOnlyDevice.getPathOnHost()).isEqualTo("/dev/sda");
        assertThat(remappedReadOnlyDevice.getPathInContainer()).isEqualTo("/dev/xvdc");
        assertThat(remappedReadOnlyDevice.getcGroupPermissions()).isEqualTo("r");
        assertThat(modeOnlyDevice.getPathInContainer()).isEqualTo("/dev/snd");
        assertThat(modeOnlyDevice.getcGroupPermissions()).isEqualTo("mr");
        assertThatThrownBy(() -> Device.parse("/dev/sda:/dev/xvdc:rx"))
                .isInstanceOf(IllegalArgumentException.class);

        LogConfig logConfig = new LogConfig(LoggingType.JSON_FILE, Map.of("max-size", "10m", "max-file", "3"));
        Ulimit nofileLimit = new Ulimit("nofile", 1024L, 2048L);
        DeviceRequest gpuRequest = new DeviceRequest()
                .withDriver("nvidia")
                .withCount(1)
                .withDeviceIds(List.of("GPU-123"))
                .withCapabilities(List.of(List.of("gpu", "utility")))
                .withOptions(Map.of("capabilities", "compute"));

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withDevices(List.of(defaultDevice, remappedReadOnlyDevice))
                .withDeviceCgroupRules(List.of("c 10:229 rwm"))
                .withDeviceRequests(List.of(gpuRequest))
                .withLogConfig(logConfig)
                .withUlimits(List.of(nofileLimit));

        assertThat(LoggingType.fromValue("json-file").getType()).isEqualTo("json-file");
        assertThat(logConfig.getType()).isEqualTo(LoggingType.JSON_FILE);
        assertThat(logConfig.getConfig()).containsEntry("max-size", "10m").containsEntry("max-file", "3");
        assertThat(nofileLimit.getName()).isEqualTo("nofile");
        assertThat(nofileLimit.getSoftLong()).isEqualTo(1024L);
        assertThat(nofileLimit.getHardLong()).isEqualTo(2048L);
        assertThat(gpuRequest.getDriver()).isEqualTo("nvidia");
        assertThat(gpuRequest.getDeviceIds()).containsExactly("GPU-123");
        assertThat(gpuRequest.getCapabilities()).containsExactly(List.of("gpu", "utility"));
        assertThat(gpuRequest.getOptions()).containsEntry("capabilities", "compute");
        assertThat(hostConfig.getDevices()).containsExactly(defaultDevice, remappedReadOnlyDevice);
        assertThat(hostConfig.getDeviceCgroupRules()).containsExactly("c 10:229 rwm");
        assertThat(hostConfig.getDeviceRequests()).containsExactly(gpuRequest);
        assertThat(hostConfig.getLogConfig()).isEqualTo(logConfig);
        assertThat(hostConfig.getUlimits()).containsExactly(nofileLimit);
    }

    @Test
    void buildsNetworkingHealthAuthAndFrameModels() {
        ContainerNetwork network = new ContainerNetwork()
                .withAliases("web", "api")
                .withEndpointId("endpoint-1")
                .withGateway("172.18.0.1")
                .withIpv4Address("172.18.0.22")
                .withIpPrefixLen(16)
                .withMacAddress("02:42:ac:12:00:16")
                .withLinks(new Link("database", "db"));

        assertThat(network.getAliases()).containsExactly("web", "api");
        assertThat(network.getEndpointId()).isEqualTo("endpoint-1");
        assertThat(network.getGateway()).isEqualTo("172.18.0.1");
        assertThat(network.getIpAddress()).isEqualTo("172.18.0.22");
        assertThat(network.getIpPrefixLen()).isEqualTo(16);
        assertThat(network.getMacAddress()).isEqualTo("02:42:ac:12:00:16");
        assertThat(network.getLinks()).containsExactly(new Link("database", "db"));

        HealthCheck healthCheck = new HealthCheck()
                .withTest(List.of("CMD-SHELL", "wget -q -O - http://localhost:8080/health || exit 1"))
                .withInterval(30_000_000_000L)
                .withTimeout(5_000_000_000L)
                .withRetries(3)
                .withStartPeriod(10_000_000_000L);
        assertThat(healthCheck.getTest()).containsExactly(
                "CMD-SHELL", "wget -q -O - http://localhost:8080/health || exit 1");
        assertThat(healthCheck.getInterval()).isEqualTo(30_000_000_000L);
        assertThat(healthCheck.getTimeout()).isEqualTo(5_000_000_000L);
        assertThat(healthCheck.getRetries()).isEqualTo(3);
        assertThat(healthCheck.getStartPeriod()).isEqualTo(10_000_000_000L);

        AuthConfig authConfig = new AuthConfig()
                .withUsername("robot")
                .withPassword("secret")
                .withEmail("robot@example.test")
                .withRegistryAddress(AuthConfig.DEFAULT_SERVER_ADDRESS)
                .withAuth("encoded")
                .withIdentityToken("identity-token")
                .withRegistrytoken("registry-token");
        authConfig.setStackOrchestrator("swarm");

        assertThat(authConfig.getUsername()).isEqualTo("robot");
        assertThat(authConfig.getPassword()).isEqualTo("secret");
        assertThat(authConfig.getEmail()).isEqualTo("robot@example.test");
        assertThat(authConfig.getRegistryAddress()).isEqualTo(AuthConfig.DEFAULT_SERVER_ADDRESS);
        assertThat(authConfig.getAuth()).isEqualTo("encoded");
        assertThat(authConfig.getIdentitytoken()).isEqualTo("identity-token");
        assertThat(authConfig.getRegistrytoken()).isEqualTo("registry-token");
        assertThat(authConfig.getStackOrchestrator()).isEqualTo("swarm");
        assertThat(authConfig.toString())
                .contains("username=robot", "registryAddress=" + AuthConfig.DEFAULT_SERVER_ADDRESS);
        assertThat(authConfig.toString()).doesNotContain("secret", "identity-token", "registry-token");

        Frame frame = new Frame(StreamType.STDOUT, "hello".getBytes());
        assertThat(frame.getStreamType()).isEqualTo(StreamType.STDOUT);
        assertThat(frame.getPayload()).containsExactly((byte) 'h', (byte) 'e', (byte) 'l', (byte) 'l', (byte) 'o');
    }

    @Test
    void buildsSwarmServiceSpecifications() {
        ContainerSpecFile secretFile = new ContainerSpecFile()
                .withName("/run/secrets/api-key")
                .withUid("1000")
                .withGid("1000")
                .withMode(0400L);
        ContainerSpecFile configFile = new ContainerSpecFile()
                .withName("/etc/app/config.yml")
                .withUid("0")
                .withGid("0")
                .withMode(0444L);
        ContainerDNSConfig dnsConfig = new ContainerDNSConfig()
                .withNameservers(List.of("1.1.1.1", "8.8.8.8"))
                .withSearch(List.of("service.local"))
                .withOptions(List.of("ndots:1"));
        ContainerSpec containerSpec = new ContainerSpec()
                .withImage("busybox:latest")
                .withCommand(List.of("/bin/sh", "-c"))
                .withArgs(List.of("while true; do sleep 60; done"))
                .withEnv(List.of("APP_ENV=test"))
                .withDir("/srv/app")
                .withUser("1000:1000")
                .withGroups("1000")
                .withTty(false)
                .withOpenStdin(false)
                .withReadOnly(true)
                .withHosts(List.of("127.0.0.1 local.test"))
                .withHostname("worker")
                .withStopSignal("SIGTERM")
                .withStopGracePeriod(10_000_000_000L)
                .withDnsConfig(dnsConfig)
                .withSecrets(List.of(new ContainerSpecSecret()
                        .withSecretId("secret-id")
                        .withSecretName("api-key")
                        .withFile(secretFile)))
                .withConfigs(List.of(new ContainerSpecConfig()
                        .withConfigID("config-id")
                        .withConfigName("app-config")
                        .withFile(configFile)));

        ResourceRequirements resources = new ResourceRequirements()
                .withLimits(new ResourceSpecs().withMemoryBytes(256L * 1024L * 1024L).withNanoCPUs(1_000_000_000L))
                .withReservations(new ResourceSpecs().withMemoryBytes(64L * 1024L * 1024L).withNanoCPUs(250_000_000L));
        ServiceRestartPolicy restartPolicy = new ServiceRestartPolicy()
                .withCondition(ServiceRestartCondition.ON_FAILURE)
                .withDelay(5_000_000_000L)
                .withMaxAttempts(3L)
                .withWindow(60_000_000_000L);
        ServicePlacement placement = new ServicePlacement()
                .withConstraints(List.of("node.labels.zone == test"))
                .withMaxReplicas(2);
        NetworkAttachmentConfig taskNetwork = new NetworkAttachmentConfig()
                .withTarget("backend")
                .withAliases(List.of("worker", "jobs"));
        Driver logDriver = new Driver()
                .withName("json-file")
                .withOptions(Map.of("max-size", "5m"));
        TaskSpec taskSpec = new TaskSpec()
                .withContainerSpec(containerSpec)
                .withResources(resources)
                .withRestartPolicy(restartPolicy)
                .withPlacement(placement)
                .withLogDriver(logDriver)
                .withForceUpdate(1)
                .withRuntime("container")
                .withNetworks(List.of(taskNetwork));

        PortConfig publishedPort = new PortConfig()
                .withName("http")
                .withProtocol(PortConfigProtocol.TCP)
                .withTargetPort(8080)
                .withPublishedPort(18080)
                .withPublishMode(PublishMode.ingress);
        EndpointSpec endpointSpec = new EndpointSpec()
                .withMode(EndpointResolutionMode.VIP)
                .withPorts(List.of(publishedPort));
        UpdateConfig updateConfig = new UpdateConfig()
                .withParallelism(2L)
                .withDelay(1_000_000_000L)
                .withFailureAction(UpdateFailureAction.ROLLBACK)
                .withMaxFailureRatio(0.25F)
                .withMonitor(30_000_000_000L)
                .withOrder(UpdateOrder.START_FIRST);
        ServiceModeConfig modeConfig = new ServiceModeConfig()
                .withReplicated(new ServiceReplicatedModeOptions().withReplicas(3));
        ServiceSpec serviceSpec = new ServiceSpec()
                .withName("background-worker")
                .withLabels(Map.of("team", "platform"))
                .withTaskTemplate(taskSpec)
                .withMode(modeConfig)
                .withNetworks(List.of(taskNetwork))
                .withEndpointSpec(endpointSpec)
                .withUpdateConfig(updateConfig)
                .withRollbackConfig(new UpdateConfig().withParallelism(1L));

        assertThat(containerSpec.getImage()).isEqualTo("busybox:latest");
        assertThat(containerSpec.getCommand()).containsExactly("/bin/sh", "-c");
        assertThat(containerSpec.getArgs()).containsExactly("while true; do sleep 60; done");
        assertThat(containerSpec.getDnsConfig().getNameservers()).containsExactly("1.1.1.1", "8.8.8.8");
        assertThat(containerSpec.getSecrets().get(0).getFile()).isEqualTo(secretFile);
        assertThat(containerSpec.getConfigs().get(0).getConfigName()).isEqualTo("app-config");
        assertThat(resources.getLimits().getMemoryBytes()).isEqualTo(256L * 1024L * 1024L);
        assertThat(resources.getReservations().getNanoCPUs()).isEqualTo(250_000_000L);
        assertThat(restartPolicy.getCondition()).isEqualTo(ServiceRestartCondition.ON_FAILURE);
        assertThat(restartPolicy.getMaxAttempts()).isEqualTo(3L);
        assertThat(placement.getConstraints()).containsExactly("node.labels.zone == test");
        assertThat(placement.getMaxReplicas()).isEqualTo(2);
        assertThat(taskSpec.getLogDriver().getOptions()).containsEntry("max-size", "5m");
        assertThat(taskSpec.getNetworks()).containsExactly(taskNetwork);
        assertThat(endpointSpec.getMode()).isEqualTo(EndpointResolutionMode.VIP);
        assertThat(endpointSpec.getPorts()).containsExactly(publishedPort);
        assertThat(publishedPort.getPublishMode()).isEqualTo(PublishMode.ingress);
        assertThat(updateConfig.getFailureAction()).isEqualTo(UpdateFailureAction.ROLLBACK);
        assertThat(updateConfig.getOrder()).isEqualTo(UpdateOrder.START_FIRST);
        assertThat(modeConfig.getMode()).isEqualTo(ServiceMode.REPLICATED);
        assertThat(modeConfig.getReplicated().getReplicas()).isEqualTo(3);
        assertThat(serviceSpec.getName()).isEqualTo("background-worker");
        assertThat(serviceSpec.getLabels()).containsEntry("team", "platform");
        assertThat(serviceSpec.getTaskTemplate()).isEqualTo(taskSpec);
        assertThat(serviceSpec.getNetworks()).containsExactly(taskNetwork);
        assertThat(serviceSpec.getRollbackConfig().getParallelism()).isEqualTo(1L);

        assertThatThrownBy(() -> modeConfig.withGlobal(new ServiceGlobalModeOptions()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot set both global and replicated mode");
        assertThatThrownBy(() -> new ServicePlacement().withMaxReplicas(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultCallbackLifecycleCompletesAndPropagatesFailures() throws Exception {
        AtomicInteger closedStreams = new AtomicInteger();
        Closeable stream = closedStreams::incrementAndGet;
        ResultCallback.Adapter<String> callback = new ResultCallback.Adapter<>();

        assertThat(callback.awaitStarted(10, TimeUnit.MILLISECONDS)).isFalse();
        callback.onStart(stream);
        callback.onNext("first item");
        assertThat(callback.awaitStarted(1, TimeUnit.SECONDS)).isTrue();
        callback.onComplete();

        assertThat(callback.awaitCompletion(1, TimeUnit.SECONDS)).isTrue();
        assertThat(closedStreams).hasValue(1);
        callback.close();
        assertThat(closedStreams).hasValue(1);

        ResultCallback.Adapter<String> failingCallback = new ResultCallback.Adapter<>();
        IOException failure = new IOException("stream failed");
        failingCallback.onStart(() -> { });
        failingCallback.onError(failure);

        assertThatThrownBy(() -> failingCallback.awaitCompletion(1, TimeUnit.SECONDS))
                .isInstanceOf(RuntimeException.class)
                .hasCause(failure);
    }

    @Test
    void dockerExceptionsExposeHttpStatusAndCauses() {
        IllegalStateException cause = new IllegalStateException("root cause");
        DockerException dockerException = new DockerException("daemon unavailable", 503, cause);

        assertThat(dockerException).hasMessage("Status 503: daemon unavailable").hasCause(cause);
        assertThat(dockerException.getHttpStatus()).isEqualTo(503);
        assertThat(new BadRequestException("bad request").getHttpStatus()).isEqualTo(400);
        assertThat(new NotFoundException("missing").getHttpStatus()).isEqualTo(404);
        assertThat(new ConflictException("conflict").getHttpStatus()).isEqualTo(409);
        assertThat(new InternalServerErrorException("server error").getHttpStatus()).isEqualTo(500);
        assertThat(new DockerClientException("client error", cause)).hasMessage("client error").hasCause(cause);
    }
}
