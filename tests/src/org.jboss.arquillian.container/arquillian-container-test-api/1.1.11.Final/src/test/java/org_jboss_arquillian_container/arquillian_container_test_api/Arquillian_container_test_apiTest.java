/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.arquillian.container.test.api.Config;
import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.OverProtocol;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.ShouldThrowException;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.container.test.api.Testable;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.Assignable;
import org.jboss.shrinkwrap.api.ArchiveEventHandler;
import org.jboss.shrinkwrap.api.ArchiveFormat;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Filter;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.asset.NamedAsset;
import org.jboss.shrinkwrap.api.exporter.StreamExporter;
import org.jboss.shrinkwrap.api.formatter.Formatter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;

public class Arquillian_container_test_apiTest {
    @Test
    void configProvidesMutableFluentPropertyMap() {
        Config config = new Config();

        Config returned = config.add("bind.address", "127.0.0.1")
                .add("http.port", "8080")
                .add("http.port", "8181");

        assertThat(returned).isSameAs(config);
        assertThat(config.map()).isSameAs(config.getProperties());
        assertThat(config.map())
                .containsEntry("bind.address", "127.0.0.1")
                .containsEntry("http.port", "8181")
                .hasSize(2);

        config.getProperties().put("debug", "true");
        assertThat(config.map()).containsEntry("debug", "true");
    }

    @Test
    void containerControllerContractAcceptsPlainAndConfiguredLifecycleOperations() {
        RecordingContainerController controller = new RecordingContainerController();
        Config config = new Config().add("host", "localhost").add("port", "9090");

        controller.start("primary");
        controller.start("secondary", config.map());
        controller.stop("primary");
        controller.kill("secondary");

        assertThat(controller.isStarted("primary")).isFalse();
        assertThat(controller.isStarted("secondary")).isFalse();
        assertThat(controller.events).containsExactly(
                "start:primary",
                "start:secondary",
                "stop:primary",
                "kill:secondary");
        assertThat(controller.configurations.get("primary")).isEmpty();
        assertThat(controller.configurations.get("secondary"))
                .containsEntry("host", "localhost")
                .containsEntry("port", "9090");
    }

    @Test
    void deployerContractExposesManualDeploymentLifecycleAndPayload() throws Exception {
        RecordingDeployer deployer = new RecordingDeployer();
        deployer.register("orders", "archive-bytes");

        deployer.deploy("orders");
        String payload;
        try (InputStream stream = deployer.getDeployment("orders")) {
            payload = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        deployer.undeploy("orders");

        assertThat(payload).isEqualTo("archive-bytes");
        assertThat(deployer.deployed).doesNotContain("orders");
        assertThat(deployer.events).containsExactly("deploy:orders", "get:orders", "undeploy:orders");
    }

    @Test
    void testableMarksArchiveAndKeepsArchiveType() {
        RecordingArchive archive = new RecordingArchive("web.war");

        assertThat(Testable.isArchiveToTest(archive)).isFalse();
        RecordingArchive returned = Testable.archiveToTest(archive);

        assertThat(returned).isSameAs(archive);
        assertThat(Testable.isArchiveToTest(archive)).isTrue();
        assertThat(archive.addedPaths).containsExactly(Testable.MARKER_FILE_PATH.get());
        assertThat(archive.addedAssets).hasSize(1);
        assertThat(Testable.MARKER_FILE_PATH.get()).isEqualTo("/META-INF/arquillian.ArchiveUnderTest");
    }

    @Test
    void testableAddsRetrievableEmptyMarkerAssetToRealArchive() throws Exception {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "sample.jar");

        JavaArchive returned = Testable.archiveToTest(archive);
        Node marker = archive.get(Testable.MARKER_FILE_PATH);

        assertThat(returned).isSameAs(archive);
        assertThat(Testable.isArchiveToTest(archive)).isTrue();
        assertThat(marker).isNotNull();
        assertThat(marker.getPath()).isEqualTo(Testable.MARKER_FILE_PATH);

        byte[] markerBytes;
        try (InputStream markerStream = marker.getAsset().openStream()) {
            markerBytes = markerStream.readAllBytes();
        }
        assertThat(markerBytes).isEmpty();
    }

    @Test
    void testableRecognizesArchiveMarkedWithPublicMarkerPath() {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "pre-marked.jar");

        archive.add(EmptyAsset.INSTANCE, Testable.MARKER_FILE_PATH);

        assertThat(Testable.isArchiveToTest(archive)).isTrue();
    }

    @Test
    void deploymentAnnotationsExposeDefaultAndCustomizedValuesAtRuntime() throws Exception {
        Method defaultDeployment = DeploymentFixture.class.getDeclaredMethod("defaultDeployment");
        Method manualDeployment = DeploymentFixture.class.getDeclaredMethod("manualDeployment");

        Deployment defaultAnnotation = defaultDeployment.getAnnotationsByType(Deployment.class)[0];
        Deployment manualAnnotation = manualDeployment.getAnnotationsByType(Deployment.class)[0];

        assertThat(defaultAnnotation.name()).isEqualTo("_DEFAULT_");
        assertThat(defaultAnnotation.managed()).isTrue();
        assertThat(defaultAnnotation.order()).isEqualTo(-1);
        assertThat(defaultAnnotation.testable()).isTrue();

        assertThat(manualAnnotation.name()).isEqualTo("orders");
        assertThat(manualAnnotation.managed()).isFalse();
        assertThat(manualAnnotation.order()).isEqualTo(7);
        assertThat(manualAnnotation.testable()).isFalse();
        assertRuntimeMethodTarget(Deployment.class);
    }

    @Test
    void deploymentRelatedAnnotationsExposeTheirConfiguredValues() throws Exception {
        Method annotatedDeployment = DeploymentFixture.class.getDeclaredMethod("annotatedDeployment");
        Method expectedFailureDeployment = DeploymentFixture.class.getDeclaredMethod("expectedFailureDeployment");
        Method defaultFailureDeployment = DeploymentFixture.class.getDeclaredMethod("defaultFailureDeployment");

        assertThat(annotatedDeployment.getAnnotationsByType(TargetsContainer.class)[0].value()).isEqualTo("managed-server");
        assertThat(annotatedDeployment.getAnnotationsByType(OverProtocol.class)[0].value()).isEqualTo("Servlet 3.0");
        assertThat(expectedFailureDeployment.getAnnotationsByType(ShouldThrowException.class)[0].value())
                .isEqualTo(IllegalStateException.class);
        assertThat(defaultFailureDeployment.getAnnotationsByType(ShouldThrowException.class)[0].value())
                .isEqualTo(Exception.class);

        assertRuntimeMethodTarget(OverProtocol.class);
        assertRuntimeMethodTarget(ShouldThrowException.class);
        assertThat(TargetsContainer.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(TargetsContainer.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactlyInAnyOrder(ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER);
    }

    @Test
    void operationAnnotationsCanTargetMethodsFieldsAndParameters() throws Exception {
        Method operation = DeploymentFixture.class.getDeclaredMethod("operate", String.class, String.class);
        Field targetContainer = DeploymentFixture.class.getDeclaredField("targetContainer");
        Field relatedDeployment = DeploymentFixture.class.getDeclaredField("relatedDeployment");
        Parameter containerParameter = operation.getParameters()[0];
        Parameter deploymentParameter = operation.getParameters()[1];

        assertThat(operation.getAnnotationsByType(OperateOnDeployment.class)[0].value()).isEqualTo("orders");
        assertThat(operation.getAnnotationsByType(RunAsClient.class)[0]).isNotNull();
        assertThat(targetContainer.getAnnotationsByType(TargetsContainer.class)[0].value()).isEqualTo("managed-server");
        assertThat(relatedDeployment.getAnnotationsByType(OperateOnDeployment.class)[0].value()).isEqualTo("billing");
        assertThat(containerParameter.getAnnotationsByType(TargetsContainer.class)[0].value()).isEqualTo("parameter-server");
        assertThat(deploymentParameter.getAnnotationsByType(OperateOnDeployment.class)[0].value()).isEqualTo("inventory");

        assertThat(OperateOnDeployment.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(OperateOnDeployment.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactlyInAnyOrder(ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER);
    }

    @Test
    void runAsClientIsRuntimeVisibleOnMethodsTypesAndInheritedBySubclasses() throws Exception {
        Method operation = DeploymentFixture.class.getDeclaredMethod("operate", String.class, String.class);

        assertThat(ClientFixture.class.getAnnotationsByType(RunAsClient.class)[0]).isNotNull();
        assertThat(ClientSubclass.class.getAnnotationsByType(RunAsClient.class)[0]).isNotNull();
        assertThat(operation.getAnnotationsByType(RunAsClient.class)[0]).isNotNull();
        assertThat(RunAsClient.class.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(RunAsClient.class.getAnnotationsByType(Target.class)[0].value())
                .containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);
    }

    private static void assertRuntimeMethodTarget(Class<?> annotationType) {
        assertThat(annotationType.getAnnotationsByType(Retention.class)[0].value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(annotationType.getAnnotationsByType(Target.class)[0].value()).containsExactly(ElementType.METHOD);
    }

    private static final class RecordingContainerController implements ContainerController {
        private final Map<String, Boolean> started = new HashMap<String, Boolean>();
        private final Map<String, Map<String, String>> configurations = new HashMap<String, Map<String, String>>();
        private final List<String> events = new ArrayList<String>();

        @Override
        public void start(String containerQualifier) {
            start(containerQualifier, Collections.<String, String>emptyMap());
        }

        @Override
        public void start(String containerQualifier, Map<String, String> config) {
            Map<String, String> copiedConfig = new HashMap<String, String>(config);
            started.put(containerQualifier, Boolean.TRUE);
            configurations.put(containerQualifier, copiedConfig);
            events.add("start:" + containerQualifier);
        }

        @Override
        public void stop(String containerQualifier) {
            started.put(containerQualifier, Boolean.FALSE);
            events.add("stop:" + containerQualifier);
        }

        @Override
        public void kill(String containerQualifier) {
            started.put(containerQualifier, Boolean.FALSE);
            events.add("kill:" + containerQualifier);
        }

        @Override
        public boolean isStarted(String containerQualifier) {
            return Boolean.TRUE.equals(started.get(containerQualifier));
        }
    }

    private static final class RecordingDeployer implements Deployer {
        private final Set<String> deployed = new HashSet<String>();
        private final Map<String, byte[]> archives = new HashMap<String, byte[]>();
        private final List<String> events = new ArrayList<String>();

        void register(String name, String content) {
            archives.put(name, content.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void deploy(String name) {
            deployed.add(name);
            events.add("deploy:" + name);
        }

        @Override
        public InputStream getDeployment(String name) {
            events.add("get:" + name);
            return new ByteArrayInputStream(archives.get(name));
        }

        @Override
        public void undeploy(String name) {
            deployed.remove(name);
            events.add("undeploy:" + name);
        }
    }

    private static final class RecordingArchive implements Archive<RecordingArchive> {
        private final String name;
        private final Set<String> paths = new HashSet<String>();
        private final List<String> addedPaths = new ArrayList<String>();
        private final List<Asset> addedAssets = new ArrayList<Asset>();

        RecordingArchive(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getId() {
            return name;
        }

        @Override
        public RecordingArchive add(Asset asset, ArchivePath target) throws IllegalArgumentException {
            paths.add(target.get());
            addedPaths.add(target.get());
            addedAssets.add(asset);
            return this;
        }

        @Override
        public RecordingArchive add(Asset asset, ArchivePath target, String name) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(Asset asset, String target, String name) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(NamedAsset asset) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(Asset asset, String target) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addAsDirectory(String path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addAsDirectories(String... paths) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addAsDirectory(ArchivePath path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addAsDirectories(ArchivePath... paths) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive addHandlers(ArchiveEventHandler... handlers) {
            return unsupported();
        }

        @Override
        public Node get(ArchivePath path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public Node get(String path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, String path) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, ArchivePath path) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> Collection<X> getAsType(Class<X> type, Filter<ArchivePath> filter) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, String path, ArchiveFormat archiveFormat) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> X getAsType(Class<X> type, ArchivePath path, ArchiveFormat archiveFormat) {
            return unsupported();
        }

        @Override
        public <X extends Archive<X>> Collection<X> getAsType(
                Class<X> type, Filter<ArchivePath> filter, ArchiveFormat archiveFormat) {
            return unsupported();
        }

        @Override
        public boolean contains(ArchivePath path) throws IllegalArgumentException {
            return paths.contains(path.get());
        }

        @Override
        public boolean contains(String path) throws IllegalArgumentException {
            return paths.contains(path);
        }

        @Override
        public Node delete(ArchivePath path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public Node delete(String path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public Map<ArchivePath, Node> getContent() {
            return Collections.emptyMap();
        }

        @Override
        public Map<ArchivePath, Node> getContent(Filter<ArchivePath> filter) {
            return Collections.emptyMap();
        }

        @Override
        public RecordingArchive add(Archive<?> archive, ArchivePath path, Class<? extends StreamExporter> exporter)
                throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive add(Archive<?> archive, String path, Class<? extends StreamExporter> exporter)
                throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, Filter<ArchivePath> filter) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, ArchivePath path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, String path) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, ArchivePath path, Filter<ArchivePath> filter)
                throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive merge(Archive<?> source, String path, Filter<ArchivePath> filter)
                throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive filter(Filter<ArchivePath> filter) {
            return unsupported();
        }

        @Override
        public RecordingArchive move(ArchivePath source, ArchivePath target) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public RecordingArchive move(String source, String target) throws IllegalArgumentException {
            return unsupported();
        }

        @Override
        public String toString(boolean verbose) {
            return name;
        }

        @Override
        public String toString(Formatter formatter) throws IllegalArgumentException {
            return name;
        }

        @Override
        public void writeTo(OutputStream outputStream, Formatter formatter) throws IllegalArgumentException {
            throw new UnsupportedOperationException("Only marker operations are needed by these tests");
        }

        @Override
        public Archive<RecordingArchive> shallowCopy() {
            return unsupported();
        }

        @Override
        public Archive<RecordingArchive> shallowCopy(Filter<ArchivePath> filter) {
            return unsupported();
        }

        @Override
        public <TYPE extends Assignable> TYPE as(Class<TYPE> type) {
            return unsupported();
        }

        private static <T> T unsupported() {
            throw new UnsupportedOperationException("Only marker operations are needed by these tests");
        }
    }

    private static final class DeploymentFixture {
        @TargetsContainer("managed-server")
        private String targetContainer;

        @OperateOnDeployment("billing")
        private String relatedDeployment;

        @Deployment
        static RecordingArchive defaultDeployment() {
            return new RecordingArchive("default.war");
        }

        @Deployment(name = "orders", managed = false, order = 7, testable = false)
        static RecordingArchive manualDeployment() {
            return new RecordingArchive("orders.war");
        }

        @Deployment(name = "protocol")
        @TargetsContainer("managed-server")
        @OverProtocol("Servlet 3.0")
        static RecordingArchive annotatedDeployment() {
            return new RecordingArchive("protocol.war");
        }

        @Deployment(name = "expectedFailure")
        @ShouldThrowException(IllegalStateException.class)
        static RecordingArchive expectedFailureDeployment() {
            return new RecordingArchive("failure.war");
        }

        @Deployment(name = "defaultFailure")
        @ShouldThrowException
        static RecordingArchive defaultFailureDeployment() {
            return new RecordingArchive("default-failure.war");
        }

        @RunAsClient
        @OperateOnDeployment("orders")
        void operate(@TargetsContainer("parameter-server") String container,
                @OperateOnDeployment("inventory") String deployment) {
        }
    }

    @RunAsClient
    private static class ClientFixture {
    }

    private static final class ClientSubclass extends ClientFixture {
    }
}
