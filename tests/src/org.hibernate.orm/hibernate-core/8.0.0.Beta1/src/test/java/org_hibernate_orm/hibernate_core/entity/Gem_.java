/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core.entity;

import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

/// Hand-written JPA static metamodel that Hibernate populates for [Gem].
@StaticMetamodel(Gem.class)
public class Gem_ {

    public static final String NAME = "name";

    public static final String CARATS = "carats";

    public static volatile SingularAttribute<Gem, Long> id;

    public static volatile SingularAttribute<Gem, String> name;

    public static volatile SingularAttribute<Gem, Integer> carats;
}
