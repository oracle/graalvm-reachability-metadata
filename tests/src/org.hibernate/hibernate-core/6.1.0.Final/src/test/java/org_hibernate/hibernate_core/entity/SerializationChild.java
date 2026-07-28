/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "SERIALIZATION_CHILD")
public class SerializationChild implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "LOOKUP_PARENT_CODE", referencedColumnName = "CODE")
    private SerializationParent lookupParent;

    public SerializationChild() {
    }

    public SerializationChild(String name, SerializationParent lookupParent) {
        this.name = name;
        this.lookupParent = lookupParent;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public SerializationParent getLookupParent() {
        return lookupParent;
    }
}
