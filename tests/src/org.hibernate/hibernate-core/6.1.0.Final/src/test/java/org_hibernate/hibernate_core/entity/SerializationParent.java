/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core.entity;

import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.SelectBeforeUpdate;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@SelectBeforeUpdate
@Table(name = "SERIALIZATION_PARENT")
public class SerializationParent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.TABLE)
    private Long id;

    @NaturalId
    @Column(nullable = false, unique = true)
    private String code;

    private String description;

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "ARRAY_PARENT_ID")
    private SerializationChild[] children;

    public SerializationParent() {
    }

    public SerializationParent(String code, String description, SerializationChild[] children) {
        this.code = code;
        this.description = description;
        this.children = children;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SerializationChild[] getChildren() {
        return children;
    }

    public void setChildren(SerializationChild[] children) {
        this.children = children;
    }
}
