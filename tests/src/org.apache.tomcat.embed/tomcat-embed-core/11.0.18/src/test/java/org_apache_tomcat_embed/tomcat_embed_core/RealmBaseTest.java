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

import org.apache.catalina.realm.RealmBase;
import org.junit.jupiter.api.Test;

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
}
