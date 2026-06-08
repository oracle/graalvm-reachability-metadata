/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_plugins.maven_compiler_plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.compiler.AbstractCompilerMojo;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.junit.jupiter.api.Test;

public class AbstractCompilerMojoTest {
    @Test
    void requestValuesAreReadThroughMavenCompatibilityReflection() throws Exception {
        Date startTime = new Date(123456789L);
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setThreadCount("4");
        request.setStartTime(startTime);

        TestCompilerMojo mojo = new TestCompilerMojo();
        MojoFieldInjector injector = new MojoFieldInjector();
        injector.inject(mojo, "session",
                new MavenSession(null, request, new DefaultMavenExecutionResult(), new MavenProject()));

        assertEquals(4, mojo.requestThreadCount());
        assertEquals(startTime, mojo.buildStartTime());
    }

    @Test
    void toolchainRequirementsUseNewerToolchainManagerMethodWhenPresent() throws Exception {
        TestToolchain expectedToolchain = new TestToolchain();
        TestToolchainManager toolchainManager = new TestToolchainManager(expectedToolchain);
        Map<String, String> requirements = new HashMap<>();
        requirements.put("version", "21");

        TestCompilerMojo mojo = new TestCompilerMojo();
        MojoFieldInjector injector = new MojoFieldInjector();
        injector.inject(mojo, "session", new MavenSession(null, new DefaultMavenExecutionRequest(),
                new DefaultMavenExecutionResult(), new MavenProject()));
        injector.inject(mojo, "toolchainManager", toolchainManager);
        injector.inject(mojo, "jdkToolchain", requirements);

        assertSame(expectedToolchain, mojo.toolchain());
        assertEquals("jdk", toolchainManager.requestedType);
        assertSame(requirements, toolchainManager.requestedRequirements);
    }

    static final class MojoFieldInjector extends AbstractMojoTestCase {
        void inject(Object target, String name, Object value) throws IllegalAccessException {
            setVariableValueToObject(target, name, value);
        }
    }

    static final class TestCompilerMojo extends AbstractCompilerMojo {
        int requestThreadCount() {
            return getRequestThreadCount();
        }

        Date buildStartTime() {
            return getBuildStartTime();
        }

        Toolchain toolchain() {
            return getToolchain();
        }

        @Override
        protected SourceInclusionScanner getSourceInclusionScanner(int staleMillis) {
            return null;
        }

        @Override
        protected SourceInclusionScanner getSourceInclusionScanner(String inputFileEnding) {
            return null;
        }

        @Override
        protected List<String> getClasspathElements() {
            return Collections.emptyList();
        }

        @Override
        protected List<String> getModulepathElements() {
            return Collections.emptyList();
        }

        @Override
        protected Map<String, JavaModuleDescriptor> getPathElements() {
            return Collections.emptyMap();
        }

        @Override
        protected List<String> getCompileSourceRoots() {
            return Collections.emptyList();
        }

        @Override
        protected void preparePaths(Set<File> sourceFiles) {
        }

        @Override
        protected File getOutputDirectory() {
            return new File("target/classes");
        }

        @Override
        protected String getSource() {
            return "1.8";
        }

        @Override
        protected String getTarget() {
            return "1.8";
        }

        @Override
        protected String getRelease() {
            return null;
        }

        @Override
        protected String getCompilerArgument() {
            return null;
        }

        @Override
        protected Map<String, String> getCompilerArguments() {
            return Collections.emptyMap();
        }

        @Override
        protected File getGeneratedSourcesDirectory() {
            return new File("target/generated-sources/annotations");
        }
    }

    public static final class TestToolchainManager implements ToolchainManager {
        private final Toolchain toolchain;
        private String requestedType;
        private Map<String, String> requestedRequirements;

        TestToolchainManager(Toolchain toolchain) {
            this.toolchain = toolchain;
        }

        public List<Toolchain> getToolchains(MavenSession session, String type, Map<String, String> requirements) {
            requestedType = type;
            requestedRequirements = requirements;
            List<Toolchain> toolchains = new ArrayList<>();
            toolchains.add(toolchain);
            return toolchains;
        }

        @Override
        public Toolchain getToolchainFromBuildContext(String type, MavenSession session) {
            return null;
        }
    }

    static final class TestToolchain implements Toolchain {
        @Override
        public String getType() {
            return "jdk";
        }

        @Override
        public String findTool(String toolName) {
            return toolName;
        }
    }
}
