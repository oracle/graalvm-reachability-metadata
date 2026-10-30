/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.builder_annotations;

import io.sundr.builder.annotations.Buildable;

import java.util.List;
import java.util.Map;

@Buildable
public class Shipment {
    private final String reference;
    private final Address destination;
    private final List<String> items;
    private final Map<String, String> attributes;

    public Shipment(String reference, Address destination, List<String> items, Map<String, String> attributes) {
        this.reference = reference;
        this.destination = destination;
        this.items = items;
        this.attributes = attributes;
    }

    public String getReference() {
        return reference;
    }

    public Address getDestination() {
        return destination;
    }

    public List<String> getItems() {
        return items;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }
}
