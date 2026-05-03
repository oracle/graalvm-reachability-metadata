/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_play.play_build_link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import play.TemplateImports;
import play.api.PlayException;
import play.core.Build;
import play.core.BuildDocHandler;
import play.core.BuildLink;
import play.core.server.ReloadableServer;

public class Play_build_linkTest {
    @Test
    void templateImportsExposeExpectedJavaAndScalaDefaults() {
        assertThat(TemplateImports.minimalJavaTemplateImports)
                .containsExactly(
                        "models._",
                        "controllers._",
                        "play.api.i18n._",
                        "views.%format%._",
                        "play.api.templates.PlayMagic._",
                        "java.lang._",
                        "java.util._",
                        "play.core.j.PlayMagicForJava._",
                        "play.mvc._",
                        "play.api.data.Field");

        assertThat(TemplateImports.defaultJavaTemplateImports)
                .containsExactly(
                        "models._",
                        "controllers._",
                        "play.api.i18n._",
                        "views.%format%._",
                        "play.api.templates.PlayMagic._",
                        "java.lang._",
                        "java.util._",
                        "play.core.j.PlayMagicForJava._",
                        "play.mvc._",
                        "play.api.data.Field",
                        "play.data._",
                        "play.core.j.PlayFormsMagicForJava._");

        assertThat(TemplateImports.defaultScalaTemplateImports)
                .containsExactly(
                        "models._",
                        "controllers._",
                        "play.api.i18n._",
                        "views.%format%._",
                        "play.api.templates.PlayMagic._",
                        "play.api.mvc._",
                        "play.api.data._");
    }

    @Test
    void templateImportsCanBeSpecializedForTemplateFormats() {
        List<String> javaHtmlImports = importsForFormat(TemplateImports.defaultJavaTemplateImports, "html");
        List<String> scalaXmlImports = importsForFormat(TemplateImports.defaultScalaTemplateImports, "xml");

        assertThat(javaHtmlImports)
                .contains("views.html._")
                .doesNotContain("views.%format%._");
        assertThat(scalaXmlImports)
                .contains("views.xml._")
                .doesNotContain("views.%format%._");
    }

    @Test
    void templateImportListsAreImmutableAndUsePrefixRelationship() {
        List<String> minimalJavaImports = TemplateImports.minimalJavaTemplateImports;
        List<String> defaultJavaImports = TemplateImports.defaultJavaTemplateImports;

        assertThat(defaultJavaImports)
                .startsWith(minimalJavaImports.toArray(new String[0]))
                .endsWith("play.data._", "play.core.j.PlayFormsMagicForJava._");
        assertThat(TemplateImports.defaultScalaTemplateImports)
                .startsWith("models._", "controllers._", "play.api.i18n._", "views.%format%._");

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> minimalJavaImports.add("user.code._"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> defaultJavaImports.remove("play.data._"));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> TemplateImports.defaultScalaTemplateImports.clear());
    }

    @Test
    void sharedClassesExposeBuildAndExceptionBoundaryTypes() {
        assertThat(Build.sharedClasses)
                .containsExactly(
                        "play.core.BuildLink",
                        "play.core.BuildDocHandler",
                        "play.core.server.ReloadableServer",
                        "play.api.UsefulException",
                        "play.api.PlayException",
                        "play.api.PlayException$InterestingLines",
                        "play.api.PlayException$RichDescription",
                        "play.api.PlayException$ExceptionSource",
                        "play.api.PlayException$ExceptionAttachment");

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> Build.sharedClasses.add("play.core.NotShared"));
    }

    @Test
    void publicUtilityClassesCanBeInstantiated() {
        assertThat(new Build()).isInstanceOf(Build.class);
        assertThat(new TemplateImports()).isInstanceOf(TemplateImports.class);
    }

    @Test
    void buildLinkInterfaceSupportsReloadSourceLookupAndSettingsAccess() {
        File projectDirectory = new File("sample-play-project").getAbsoluteFile();
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("play.server.http.port", "9000");
        settings.put("play.http.secret.key", "test-secret");
        RecordingBuildLink buildLink = new RecordingBuildLink(projectDirectory, settings);

        assertThat(buildLink.reload()).isEqualTo("reload-1");
        assertThat(buildLink.reload()).isEqualTo("reload-2");
        assertThat(buildLink.findSource("controllers.HomeController.index", 42))
                .containsExactly("controllers.HomeController.index", 42, projectDirectory);
        assertThat(buildLink.projectPath()).isEqualTo(projectDirectory);
        assertThat(buildLink.settings()).containsExactlyEntriesOf(settings);

        buildLink.forceReload();
        assertThat(buildLink.reloadCount()).isEqualTo(2);
        assertThat(buildLink.forceReloadCount()).isEqualTo(1);
        assertThat(buildLink.lastSourceName()).isEqualTo("controllers.HomeController.index");
        assertThat(buildLink.lastSourceLine()).isEqualTo(42);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> buildLink.settings().put("new.setting", "value"));
    }

    @Test
    void buildLinkReloadReturnsDocumentedReloadOutcomes() {
        ClassLoader applicationClassLoader = Thread.currentThread().getContextClassLoader();
        IllegalStateException compileError = new IllegalStateException("compile failed");
        BuildLink buildLink = new ReloadOutcomeBuildLink(null, applicationClassLoader, compileError);

        assertThat(buildLink.reload()).isNull();
        assertThat(buildLink.reload()).isSameAs(applicationClassLoader);
        assertThat(buildLink.reload()).isSameAs(compileError);
    }

    @Test
    void buildDocHandlerCanAcceptOrIgnoreDocumentationRequests() {
        BuildDocHandler docHandler = request -> {
            if ("/docs/home".equals(request)) {
                return "handled:/docs/home";
            }
            return null;
        };

        assertThat(docHandler.maybeHandleDocRequest("/docs/home")).isEqualTo("handled:/docs/home");
        assertThat(docHandler.maybeHandleDocRequest("/assets/app.js")).isNull();
    }

    @Test
    void buildDocHandlerSupportsTypedDocumentationRequests() {
        BuildDocHandler docHandler = request -> {
            if (request instanceof DocumentationRequest documentationRequest
                    && "java-api".equals(documentationRequest.section())) {
                return documentationRequest.documentationPath();
            }
            return null;
        };

        assertThat(docHandler.maybeHandleDocRequest(new DocumentationRequest("java-api", "routing")))
                .isEqualTo("/docs/java-api/routing");
        assertThat(docHandler.maybeHandleDocRequest(new DocumentationRequest("scala-api", "routing"))).isNull();
    }

    @Test
    void reloadableServerInterfaceExposesLifecycleCallbacks() {
        RecordingReloadableServer server = new RecordingReloadableServer();

        server.reload();
        server.reload();
        server.stop();

        assertThat(server.reloadCount()).isEqualTo(2);
        assertThat(server.stopCount()).isEqualTo(1);
        assertThat(server.isStopped()).isTrue();
    }

    @Test
    void exceptionSourcesExposeFocusedInterestingLinesForErrorReporting() {
        SourceBackedPlayException exception = new SourceBackedPlayException(
                "Compilation error",
                "Unexpected token in route file",
                "conf/routes",
                "GET     /          controllers.HomeController.index\n"
                        + "GET     /health    controllers.HealthController.check\n"
                        + "BROKEN  route      controllers.BrokenController.show\n"
                        + "POST    /items     controllers.ItemController.create\n"
                        + "GET     /items     controllers.ItemController.list",
                3,
                8);

        PlayException.InterestingLines interestingLines = exception.interestingLines(1);

        assertThat(exception).isInstanceOf(PlayException.class);
        assertThat(exception.sourceName()).isEqualTo("conf/routes");
        assertThat(exception.line()).isEqualTo(3);
        assertThat(exception.position()).isEqualTo(8);
        assertThat(interestingLines.firstLine).isEqualTo(2);
        assertThat(interestingLines.errorLine).isEqualTo(1);
        assertThat(interestingLines.focus)
                .containsExactly(
                        "GET     /health    controllers.HealthController.check",
                        "BROKEN  route      controllers.BrokenController.show",
                        "POST    /items     controllers.ItemController.create");
    }

    private static List<String> importsForFormat(List<String> templateImports, String format) {
        List<String> resolvedImports = new ArrayList<>(templateImports.size());
        for (String templateImport : templateImports) {
            resolvedImports.add(templateImport.replace("%format%", format));
        }
        return resolvedImports;
    }

    private static final class DocumentationRequest {
        private final String section;
        private final String page;

        private DocumentationRequest(String section, String page) {
            this.section = section;
            this.page = page;
        }

        private String section() {
            return section;
        }

        private String documentationPath() {
            return "/docs/" + section + "/" + page;
        }
    }

    private static final class RecordingBuildLink implements BuildLink {
        private final File projectDirectory;
        private final Map<String, String> settings;
        private int reloadCount;
        private int forceReloadCount;
        private String lastSourceName;
        private Integer lastSourceLine;

        private RecordingBuildLink(File projectDirectory, Map<String, String> settings) {
            this.projectDirectory = projectDirectory;
            this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
        }

        @Override
        public Object reload() {
            reloadCount++;
            return "reload-" + reloadCount;
        }

        @Override
        public Object[] findSource(String name, Integer line) {
            lastSourceName = name;
            lastSourceLine = line;
            return new Object[] {name, line, projectDirectory};
        }

        @Override
        public File projectPath() {
            return projectDirectory;
        }

        @Override
        public void forceReload() {
            forceReloadCount++;
        }

        @Override
        public Map<String, String> settings() {
            return settings;
        }

        private int reloadCount() {
            return reloadCount;
        }

        private int forceReloadCount() {
            return forceReloadCount;
        }

        private String lastSourceName() {
            return lastSourceName;
        }

        private Integer lastSourceLine() {
            return lastSourceLine;
        }
    }

    private static final class SourceBackedPlayException extends PlayException.ExceptionSource {
        private final String sourceName;
        private final String input;
        private final Integer line;
        private final Integer position;

        private SourceBackedPlayException(
                String title, String description, String sourceName, String input, Integer line, Integer position) {
            super(title, description);
            this.sourceName = sourceName;
            this.input = input;
            this.line = line;
            this.position = position;
        }

        @Override
        public Integer line() {
            return line;
        }

        @Override
        public Integer position() {
            return position;
        }

        @Override
        public String input() {
            return input;
        }

        @Override
        public String sourceName() {
            return sourceName;
        }
    }

    private static final class ReloadOutcomeBuildLink implements BuildLink {
        private final List<Object> reloadOutcomes;
        private int reloadIndex;

        private ReloadOutcomeBuildLink(Object... reloadOutcomes) {
            this.reloadOutcomes = Arrays.asList(reloadOutcomes);
        }

        @Override
        public Object reload() {
            return reloadOutcomes.get(reloadIndex++);
        }

        @Override
        public Object[] findSource(String name, Integer line) {
            return null;
        }

        @Override
        public File projectPath() {
            return new File(".").getAbsoluteFile();
        }

        @Override
        public void forceReload() { }

        @Override
        public Map<String, String> settings() {
            return Collections.emptyMap();
        }
    }

    private static final class RecordingReloadableServer implements ReloadableServer {
        private int reloadCount;
        private int stopCount;
        private boolean stopped;

        @Override
        public void stop() {
            stopCount++;
            stopped = true;
        }

        @Override
        public void reload() {
            reloadCount++;
        }

        private int reloadCount() {
            return reloadCount;
        }

        private int stopCount() {
            return stopCount;
        }

        private boolean isStopped() {
            return stopped;
        }
    }
}
