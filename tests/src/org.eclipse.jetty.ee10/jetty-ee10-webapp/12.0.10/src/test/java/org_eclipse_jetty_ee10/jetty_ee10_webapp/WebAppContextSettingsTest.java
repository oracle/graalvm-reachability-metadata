/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_ee10.jetty_ee10_webapp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.util.ClassMatcher;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebAppContextSettingsTest {
    @Test
    void resourceAliasesResolveAgainstBaseResourceWithoutStartingContext() throws Exception {
        Path webRoot = createTestDirectory("resource-aliases");
        try {
            Path assets = Files.createDirectories(webRoot.resolve("assets"));
            Files.writeString(assets.resolve("message.txt"), "resolved through alias", StandardCharsets.UTF_8);

            WebAppContext context = new WebAppContext();
            context.setContextPath("/aliased");
            context.setBaseResourceAsPath(webRoot);
            context.setResourceAlias("/public/message.txt", "/assets/message.txt");
            Map<String, String> aliases = new LinkedHashMap<>();
            aliases.put("/public/message.txt", "/assets/message.txt");
            aliases.put("/download.txt", "/assets/message.txt");
            context.setResourceAliases(aliases);

            assertThat(context.getContextPath()).isEqualTo("/aliased");
            assertThat(context.getResourceAlias("/public/message.txt")).isEqualTo("/assets/message.txt");
            assertThat(context.getResourceAliases())
                    .containsEntry("/public/message.txt", "/assets/message.txt")
                    .containsEntry("/download.txt", "/assets/message.txt");
            assertThat(readResource(context.getResource("/public/message.txt"))).isEqualTo("resolved through alias");
            assertThat(readResource(context.getResource("/download.txt"))).isEqualTo("resolved through alias");
            assertThat(context.removeResourceAlias("/download.txt")).isEqualTo("/assets/message.txt");
            assertThat(context.getResourceAlias("/download.txt")).isNull();
        } finally {
            deleteRecursively(webRoot);
        }
    }

    @Test
    void webAppContextStoresDescriptorClasspathAndDeploymentSettings() throws Exception {
        Path webRoot = createTestDirectory("context-settings");
        try {
            Resource extraClasspath = writeResource(webRoot.resolve("extra-classes"), "");

            WebAppContext context = new WebAppContext();
            context.setBaseResourceAsPath(webRoot);
            context.setDefaultContextPath("/default-path");
            context.setDescriptor("/WEB-INF/custom-web.xml");
            context.setDefaultsDescriptor("classpath:/org/eclipse/jetty/ee10/webapp/webdefault-ee10.xml");
            context.setOverrideDescriptors(List.of("/WEB-INF/override.xml", "/WEB-INF/second-override.xml"));
            context.setExtraClasspath(List.of(extraClasspath));
            context.setDistributable(true);
            context.setExtractWAR(false);
            context.setCopyWebDir(true);
            context.setCopyWebInf(true);
            context.setParentLoaderPriority(true);
            context.setConfigurationDiscovered(false);
            context.setLogUrlOnStart(true);
            context.setAllowDuplicateFragmentNames(true);

            assertThat(context.getContextPath()).isEqualTo("/default-path");
            assertThat(context.isContextPathDefault()).isTrue();
            assertThat(context.getDescriptor()).isEqualTo("/WEB-INF/custom-web.xml");
            assertThat(context.getDefaultsDescriptor())
                    .isEqualTo("classpath:/org/eclipse/jetty/ee10/webapp/webdefault-ee10.xml");
            assertThat(context.getOverrideDescriptors())
                    .containsExactly("/WEB-INF/override.xml", "/WEB-INF/second-override.xml");
            assertThat(context.getExtraClasspath()).containsExactly(extraClasspath);
            assertThat(context.isDistributable()).isTrue();
            assertThat(context.isExtractWAR()).isFalse();
            assertThat(context.isCopyWebDir()).isTrue();
            assertThat(context.isCopyWebInf()).isTrue();
            assertThat(context.isParentLoaderPriority()).isTrue();
            assertThat(context.isConfigurationDiscovered()).isFalse();
            assertThat(context.isLogUrlOnStart()).isTrue();
            assertThat(context.isAllowDuplicateFragmentNames()).isTrue();
        } finally {
            deleteRecursively(webRoot);
        }
    }

    @Test
    void protectedAndHiddenClassMatchersSupportAdditionsAndExclusions() {
        WebAppContext context = new WebAppContext();
        context.setProtectedClassMatcher(new ClassMatcher("com.example.api.", "-com.example.api.internal."));
        context.setHiddenClassMatcher(new ClassMatcher("com.example.container."));
        context.addProtectedClassMatcher(new ClassMatcher("com.example.shared."));
        context.addHiddenClassMatcher(new ClassMatcher("com.example.private."));

        assertThat(context.getProtectedClassMatcher().match("com.example.api.PublicEndpoint")).isTrue();
        assertThat(context.getProtectedClassMatcher().match("com.example.api.internal.SecretEndpoint")).isFalse();
        assertThat(context.getProtectedClassMatcher().match("com.example.shared.Contract")).isTrue();
        assertThat(context.getProtectedClassMatcher().match("com.example.container.ServerOnly")).isFalse();
        assertThat(context.getHiddenClassMatcher().match("com.example.container.ServerOnly")).isTrue();
        assertThat(context.getHiddenClassMatcher().match("com.example.private.Implementation")).isTrue();
        assertThat(context.getHiddenClassMatcher().match("com.example.api.PublicEndpoint")).isFalse();
    }

    private static Resource writeResource(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return ResourceFactory.root().newResource(path);
    }

    private static String readResource(Resource resource) throws IOException {
        try (InputStream inputStream = resource.newInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path createTestDirectory(String name) throws IOException {
        Path directory = Path.of("build", "tmp", "jetty-ee10-webapp-settings", name + "-" + System.nanoTime());
        return Files.createDirectories(directory);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
