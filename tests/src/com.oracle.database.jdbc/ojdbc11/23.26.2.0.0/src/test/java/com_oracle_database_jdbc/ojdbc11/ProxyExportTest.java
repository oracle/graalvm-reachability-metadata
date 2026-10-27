/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import oracle.jdbc.proxy.ProxyExport;
import oracle.jdbc.proxy.annotation.ProxyFor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProxyExportTest {
    @TempDir
    Path outputDirectory;

    @Test
    void loadsAnEmptyProxyDefinitionForExport() throws Exception {
        ProxyExport.main(new String[] {
            "-p",
            "com_oracle_database_jdbc.ojdbc11.generated",
            "-d",
            outputDirectory.toString(),
            EmptyProxyDefinition.class.getName()
        });

        try (Stream<Path> exportedFiles = Files.list(outputDirectory)) {
            assertThat(exportedFiles).isEmpty();
        }
    }

    @ProxyFor({})
    public static final class EmptyProxyDefinition { }
}
