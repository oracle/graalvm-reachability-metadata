/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.fork.ForkParser;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

public class ClassLoaderResourceTest {

    @Test
    public void forkedParserEnumeratesResourcesThroughParentClassLoader() throws Exception {
        ForkParser parser = new ForkParser(
                ForkParser.class.getClassLoader(), new ResourceEnumeratingParser());
        parser.setPoolSize(1);
        parser.setServerPulseMillis(50L);
        parser.setServerParseTimeoutMillis(5_000L);
        parser.setServerWaitTimeoutMillis(250L);
        parser.setJavaCommand(javaCommand());
        try {
            parser.parse(
                    new ByteArrayInputStream(new byte[0]),
                    new DefaultHandler(),
                    new Metadata(),
                    new ParseContext());
            Thread.sleep(1_000L);
        } catch (TikaException exception) {
            assertThat(exception.getMessage())
                    .isEqualTo("Failed to communicate with a forked parser process."
                            + " The process has most likely crashed due to some error"
                            + " like running out of memory. A new process will be"
                            + " started for the next parsing request.");
            assertThat(exception).hasCauseInstanceOf(IOException.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            parser.close();
        }
    }

    private static List<String> javaCommand() {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-Xmx64m");
        command.add("-Djava.awt.headless=true");
        return command;
    }

    private static Path javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable);
    }
}

class ResourceEnumeratingParser implements Parser, Serializable {
    private static final long serialVersionUID = 1L;
    private static final String RESOURCE_NAME = "org/apache/tika/mime/tika-mimetypes.xml";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(MediaType.OCTET_STREAM);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context)
            throws IOException, SAXException, TikaException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = loader.getResources(RESOURCE_NAME);
        if (!resources.hasMoreElements()) {
            throw new TikaException("Expected resource was not enumerated: " + RESOURCE_NAME);
        }
        while (resources.hasMoreElements()) {
            try (InputStream resourceStream = resources.nextElement().openStream()) {
                if (resourceStream.read() == -1) {
                    throw new TikaException("Expected non-empty resource: " + RESOURCE_NAME);
                }
            }
        }
    }
}
