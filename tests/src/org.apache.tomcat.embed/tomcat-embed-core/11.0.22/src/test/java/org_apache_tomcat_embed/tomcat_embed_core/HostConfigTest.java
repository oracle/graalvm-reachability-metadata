/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.catalina.Host;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class HostConfigTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void deploysContextDescriptorAtServerStartup() throws Exception {
        Path base = temporaryDirectory.resolve("descriptor-base");
        Path appBase = temporaryDirectory.resolve("descriptor-webapps");
        Path application = temporaryDirectory.resolve("descriptor-application");
        Files.createDirectories(appBase);
        Files.createDirectories(application.resolve("WEB-INF"));
        Files.writeString(application.resolve("WEB-INF/web.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0"/>
                """);
        Path configBase = base.resolve("conf/Catalina/localhost");
        Files.createDirectories(configBase);
        Files.writeString(configBase.resolve("sample.xml"),
                "<Context docBase=\"" + application.toAbsolutePath() + "\" />");

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(base.toString());
        tomcat.setPort(0);
        Host host = tomcat.getHost();
        host.setAppBase(appBase.toString());
        host.setAutoDeploy(false);
        host.setDeployOnStartup(true);
        host.addLifecycleListener(new HostConfig());

        try {
            tomcat.start();

            assertThat(host.findChild("/sample")).isNotNull();
            assertThat(host.findChild("/sample").getState()).isEqualTo(LifecycleState.STARTED);
        } finally {
            stopAndDestroy(tomcat);
        }
    }

    @Test
    void deploysWarAtServerStartup() throws Exception {
        Path appBase = temporaryDirectory.resolve("war-webapps");
        Files.createDirectories(appBase);
        Path war = appBase.resolve("sample.war");
        try (OutputStream output = Files.newOutputStream(war);
                JarOutputStream jar = new JarOutputStream(output)) {
            jar.putNextEntry(new JarEntry("WEB-INF/web.xml"));
            jar.write("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0"/>
                    """.getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(temporaryDirectory.resolve("war-base").toString());
        tomcat.setPort(0);
        StandardHost host = (StandardHost) tomcat.getHost();
        host.setAppBase(appBase.toString());
        host.setAutoDeploy(false);
        host.setDeployOnStartup(true);
        host.setUnpackWARs(false);
        host.addLifecycleListener(new HostConfig());

        try {
            tomcat.start();

            assertThat(host.findChild("/sample")).isNotNull();
            assertThat(host.findChild("/sample").getState()).isEqualTo(LifecycleState.STARTED);
        } finally {
            stopAndDestroy(tomcat);
        }
    }

    @Test
    void deploysApplicationDirectoryAtServerStartup() throws Exception {
        Path appBase = temporaryDirectory.resolve("webapps");
        Files.createDirectories(appBase.resolve("sample/WEB-INF"));
        Files.writeString(appBase.resolve("sample/WEB-INF/web.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee" version="6.0"/>
                """);

        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(temporaryDirectory.resolve("base").toString());
        tomcat.setPort(0);
        Host host = tomcat.getHost();
        host.setAppBase(appBase.toString());
        host.setAutoDeploy(false);
        host.setDeployOnStartup(true);
        host.addLifecycleListener(new HostConfig());

        try {
            tomcat.start();

            assertThat(host.findChild("/sample")).isNotNull();
            assertThat(host.findChild("/sample").getState()).isEqualTo(LifecycleState.STARTED);
        } finally {
            stopAndDestroy(tomcat);
        }
    }

    private static void stopAndDestroy(Tomcat tomcat) throws Exception {
        if (tomcat.getServer().getState().isAvailable()) {
            tomcat.stop();
        }
        if (tomcat.getServer().getState() != LifecycleState.DESTROYED) {
            tomcat.destroy();
        }
    }
}
