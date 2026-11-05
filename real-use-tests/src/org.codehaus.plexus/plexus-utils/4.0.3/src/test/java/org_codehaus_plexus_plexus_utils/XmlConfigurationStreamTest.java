/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.XmlStreamWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads and writes deployment XML with Plexus encoding-aware stream factories.
 *
 * A release service persists a deployment descriptor containing an accented
 * service label, then loads it again through the same application I/O boundary.
 */
class XmlConfigurationStreamTest {

    @Test
    void roundTripsUtf8DeploymentDescriptor(@TempDir Path workspace) throws Exception {
        Path descriptor = workspace.resolve("deployment.xml");
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<deployment><service>caf\u00e9</service><replicas>3</replicas></deployment>";

        try (XmlStreamWriter writer = WriterFactory.newXmlWriter(descriptor.toFile())) {
            writer.write(document);
        }

        StringWriter loaded = new StringWriter();
        try (XmlStreamReader reader = ReaderFactory.newXmlReader(descriptor.toFile())) {
            reader.transferTo(loaded);
        }

        assertThat(Files.size(descriptor)).isGreaterThan(0);
        assertThat(loaded.toString()).isEqualTo(document);
    }
}
