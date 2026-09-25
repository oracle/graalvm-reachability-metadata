/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.startup.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ToolTest {

    @Test
    void launchesTomcatCommandLineApplicationWithConfiguredClassLoader(@TempDir Path catalinaHome) throws Exception {
        Files.createDirectories(catalinaHome.resolve("classes"));
        Files.createDirectories(catalinaHome.resolve("lib"));

        String originalCatalinaHome = System.getProperty("catalina.home");
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setProperty("catalina.home", catalinaHome.toString());
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            Tool.main(new String[] {"org.apache.catalina.util.ServerInfo"});
        } finally {
            System.setOut(originalOut);
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            restoreProperty("catalina.home", originalCatalinaHome);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("Server version:", "Server number:", "OS Name:", "JVM Vendor:");
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
