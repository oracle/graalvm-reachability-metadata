/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core.entity;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.DialectOverride;
import org.hibernate.annotations.Where;
import org.hibernate.dialect.H2Dialect;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "DIALECT_OVERRIDE_ENTITY")
@Where(clause = "active = false")
@DialectOverride.Where(
        dialect = H2Dialect.class,
        override = @Where(clause = "active = true")
)
@Check(constraints = "active in (true, false)")
@DialectOverride.Check(
        dialect = H2Dialect.class,
        override = @Check(constraints = "active in (true, false)")
)
@DialectOverride.Check(
        dialect = H2Dialect.class,
        override = @Check(constraints = "active in (true, false)")
)
public class DialectOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private boolean active;

    private String name;

    public DialectOverrideEntity() {
    }

    public DialectOverrideEntity(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public boolean isActive() {
        return active;
    }

    public String getName() {
        return name;
    }
}
