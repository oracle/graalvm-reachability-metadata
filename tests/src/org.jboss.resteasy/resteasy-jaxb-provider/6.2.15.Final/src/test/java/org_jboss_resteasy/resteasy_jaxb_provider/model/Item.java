/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_jaxb_provider.model;

import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "itemType", namespace = "urn:inventory")
public class Item {
    public String name;

    public Item() {
    }

    public Item(String name) {
        this.name = name;
    }
}
