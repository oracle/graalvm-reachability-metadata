/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "ARRAY_OWNER")
public class ArrayOwner implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "array_owner_gen")
    private Long id;

    @ElementCollection
    @CollectionTable(name = "ARRAY_OWNER_TAG", joinColumns = @JoinColumn(name = "OWNER_ID"))
    @OrderColumn(name = "TAG_POSITION")
    @Column(name = "TAG")
    private String[] tags;

    public Long getId() {
        return id;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }
}
