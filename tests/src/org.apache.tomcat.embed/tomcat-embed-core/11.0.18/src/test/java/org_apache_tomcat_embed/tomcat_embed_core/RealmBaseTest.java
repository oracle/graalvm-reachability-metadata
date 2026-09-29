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
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.Context;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.realm.RealmBase;
import org.apache.catalina.realm.X509UsernameRetriever;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class RealmBaseTest {

    @Test
    void commandLineDigestsPasswordWithSelectedAlgorithm() throws Exception {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            RealmBase.main(new String[] {"-a", "SHA-256", "secret"});
        } finally {
            System.setOut(originalOutput);
        }

        String digestOutput = bytes.toString(StandardCharsets.UTF_8).trim();
        assertThat(digestOutput).startsWith("secret:");
        assertThat(digestOutput).doesNotEndWith(":secret");
    }

    @Test
    void commandLineCreatesConfiguredCredentialHandler() throws Exception {
        String digestOutput = captureDigest(new String[] {
                "-h", MessageDigestCredentialHandler.class.getName(), "-a", "SHA-256", "secret"});

        assertThat(digestOutput).startsWith("secret:");
        assertThat(digestOutput).doesNotEndWith(":secret");
    }

    @Test
    void initializesConfiguredCertificateUsernameRetriever(@TempDir Path directory) throws Exception {
        ConfiguredUsernameRetriever.CREATED.set(false);
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(directory.resolve("base").toString());
        Context context = tomcat.addContext("/realm", directory.toString());
        MemoryRealm realm = new MemoryRealm();
        realm.setX509UsernameRetrieverClassName(ConfiguredUsernameRetriever.class.getName());
        context.setRealm(realm);

        try {
            realm.init();
            assertThat(ConfiguredUsernameRetriever.CREATED).isTrue();
        } finally {
            realm.destroy();
            tomcat.destroy();
        }
    }

    private static String captureDigest(String[] arguments) throws Exception {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            RealmBase.main(arguments);
        } finally {
            System.setOut(originalOutput);
        }
        return bytes.toString(StandardCharsets.UTF_8).trim();
    }

    public static class ConfiguredUsernameRetriever implements X509UsernameRetriever {
        static final AtomicBoolean CREATED = new AtomicBoolean();

        public ConfiguredUsernameRetriever() {
            CREATED.set(true);
        }

        @Override
        public String getUsername(X509Certificate cert) {
            return cert.getSubjectX500Principal().getName();
        }
    }
}
