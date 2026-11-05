/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import org.glassfish.jaxb.runtime.CycleRecoverable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's cycle-recovery subsystem for a directory graph with parent links.
public class CyclicDirectoryXmlTest {

    @Test
    @Timeout(60)
    void omitsBackReferenceWhilePreservingDirectoryContents() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Directory.class);
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

        Directory directory = new Directory("documents");
        Document document = new Document("passport.pdf", 2048);
        document.directory = directory;
        directory.documents = List.of(document);

        StringWriter output = new StringWriter();
        marshaller.marshal(directory, output);

        String xml = output.toString();
        assertTrue(xml.contains("<directory>"));
        assertTrue(xml.contains("<name>documents</name>"));
        assertTrue(xml.contains("<fileName>passport.pdf</fileName>"));
        assertTrue(xml.contains("<bytes>2048</bytes>"));
        assertEquals(1, directory.recoveredCycles);
        assertSame(marshaller, directory.callbackMarshaller);
        assertFalse(xml.contains("<directory><name>documents</name>"));

        Directory restored = (Directory) context.createUnmarshaller()
                .unmarshal(new StringReader(xml));
        assertEquals("documents", restored.name);
        assertEquals(1, restored.documents.size());
        assertEquals("passport.pdf", restored.documents.get(0).fileName);
        assertEquals(2048, restored.documents.get(0).bytes);
        Directory restoredParent = restored.documents.get(0).directory;
        assertNotNull(restoredParent);
        assertNull(restoredParent.name);
        assertNull(restoredParent.documents);
        assertNotNull(restored.documents);
    }

    @XmlRootElement(name = "directory")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Directory implements CycleRecoverable {

        private String name;

        @XmlElement(name = "file")
        private List<Document> documents;

        @XmlTransient
        private int recoveredCycles;

        @XmlTransient
        private Marshaller callbackMarshaller;

        public Directory() {
        }

        private Directory(String name) {
            this.name = name;
        }

        @Override
        public Object onCycleDetected(Context context) {
            recoveredCycles++;
            callbackMarshaller = context.getMarshaller();
            return null;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Document {

        private String fileName;
        private int bytes;
        private Directory directory;

        public Document() {
        }

        private Document(String fileName, int bytes) {
            this.fileName = fileName;
            this.bytes = bytes;
        }
    }
}
