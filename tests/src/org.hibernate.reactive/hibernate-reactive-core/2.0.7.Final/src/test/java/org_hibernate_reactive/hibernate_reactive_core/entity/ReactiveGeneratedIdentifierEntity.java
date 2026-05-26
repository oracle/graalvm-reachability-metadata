/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

@Entity(name = "ReactiveGeneratedIdentifierEntity")
@Table(name = "reactive_generated_identifier_entity")
@GenericGenerator(
        name = "reactiveOnlyGenerator",
        strategy = "org_hibernate_reactive.hibernate_reactive_core.entity.ReactiveOnlyIdentifierGenerator"
)
public class ReactiveGeneratedIdentifierEntity {

    @Id
    @GeneratedValue(generator = "reactiveOnlyGenerator")
    private Long id;

    private String name;

    public ReactiveGeneratedIdentifierEntity() {
    }

    public ReactiveGeneratedIdentifierEntity(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
