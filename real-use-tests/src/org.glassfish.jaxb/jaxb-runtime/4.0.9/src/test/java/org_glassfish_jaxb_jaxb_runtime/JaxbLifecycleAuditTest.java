/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb_jaxb_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises JAXB runtime's marshaller and unmarshaller listener subsystem for an audit workflow.
public class JaxbLifecycleAuditTest {

    @Test
    @Timeout(60)
    void auditsObjectsAsTheyCrossTheXmlBoundary() throws Exception {
        JAXBContext context = JAXBContext.newInstance(Dispatch.class);
        Dispatch dispatch = new Dispatch("D-2048", List.of(new Parcel("keyboard"), new Parcel("mouse")));

        RecordingMarshallerListener marshallerListener = new RecordingMarshallerListener();
        Marshaller marshaller = context.createMarshaller();
        marshaller.setListener(marshallerListener);
        StringWriter output = new StringWriter();
        marshaller.marshal(dispatch, output);

        assertTrue(output.toString().contains("<dispatch>"));
        assertTrue(output.toString().contains("<trackingNumber>D-2048</trackingNumber>"));
        assertTrue(output.toString().contains("<description>keyboard</description>"));
        assertTrue(marshallerListener.beforeEvents.contains("before:Dispatch"));
        assertEquals(2, marshallerListener.beforeEvents.stream()
                .filter("before:Parcel"::equals).count());
        assertTrue(marshallerListener.afterEvents.contains("after:Dispatch"));
        assertEquals(2, marshallerListener.afterEvents.stream()
                .filter("after:Parcel"::equals).count());

        RecordingUnmarshallerListener unmarshallerListener = new RecordingUnmarshallerListener();
        Unmarshaller unmarshaller = context.createUnmarshaller();
        unmarshaller.setListener(unmarshallerListener);
        Dispatch restored = (Dispatch) unmarshaller.unmarshal(new StringReader(output.toString()));

        assertEquals("D-2048", restored.trackingNumber);
        assertEquals(List.of("keyboard", "mouse"),
                restored.parcels.stream().map(parcel -> parcel.description).toList());
        assertTrue(unmarshallerListener.beforeEvents.contains("before:Dispatch"));
        assertEquals(2, unmarshallerListener.beforeEvents.stream()
                .filter("before:Parcel"::equals).count());
        assertTrue(unmarshallerListener.afterEvents.contains("after:Dispatch"));
        assertEquals(2, unmarshallerListener.afterEvents.stream()
                .filter("after:Parcel"::equals).count());
        assertTrue(restored.parcels.stream().allMatch(parcel -> "unmarshalled".equals(parcel.auditState)));
    }

    public static final class RecordingMarshallerListener extends Marshaller.Listener {

        private final List<String> beforeEvents = new ArrayList<>();
        private final List<String> afterEvents = new ArrayList<>();

        @Override
        public void beforeMarshal(Object source) {
            beforeEvents.add("before:" + source.getClass().getSimpleName());
        }

        @Override
        public void afterMarshal(Object source) {
            afterEvents.add("after:" + source.getClass().getSimpleName());
        }
    }

    public static final class RecordingUnmarshallerListener extends Unmarshaller.Listener {

        private final List<String> beforeEvents = new ArrayList<>();
        private final List<String> afterEvents = new ArrayList<>();

        @Override
        public void beforeUnmarshal(Object target, Object parent) {
            beforeEvents.add("before:" + target.getClass().getSimpleName());
        }

        @Override
        public void afterUnmarshal(Object target, Object parent) {
            afterEvents.add("after:" + target.getClass().getSimpleName());
            if (target instanceof Parcel parcel) {
                parcel.auditState = "unmarshalled";
            }
        }
    }

    @XmlRootElement(name = "dispatch")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Dispatch {

        private String trackingNumber;

        @XmlElement(name = "parcel")
        private List<Parcel> parcels;

        public Dispatch() {
        }

        private Dispatch(String trackingNumber, List<Parcel> parcels) {
            this.trackingNumber = trackingNumber;
            this.parcels = parcels;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Parcel {

        private String description;

        @XmlTransient
        private String auditState;

        public Parcel() {
        }

        private Parcel(String description) {
            this.description = description;
        }
    }
}
