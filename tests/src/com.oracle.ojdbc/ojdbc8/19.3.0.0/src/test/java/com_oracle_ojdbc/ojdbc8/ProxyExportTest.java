/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.proxy.ProxyExport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ProxyExportTest {
    @TempDir
    Path outputDirectory;

    @Test
    void rejectsAnInterfaceAsAnExportSuperclassWithoutWritingClassFiles() throws Exception {
        String superclassName = OracleConnection.class.getName();

        assertThatThrownBy(() -> ProxyExport.main(new String[] {
                "-p", "com_oracle_ojdbc.ojdbc8.exported", "-d", outputDirectory.toString(), superclassName
        })).isInstanceOf(RuntimeException.class)
                .hasMessage(superclassName + " must be an abstract or concrete class");

        try (Stream<Path> files = Files.list(outputDirectory)) {
            assertThat(files).isEmpty();
        }
    }
}
