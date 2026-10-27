/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/// An entity with a hand-written JPA static metamodel in [Gem_].
@Entity(name = "Gem")
public class Gem {

    @Id
    private Long id;

    private String name;

    private Integer carats;

    public Gem() {
    }

    public Gem(Long id, String name, Integer carats) {
        this.id = id;
        this.name = name;
        this.carats = carats;
    }

    public String getName() {
        return name;
    }
}
