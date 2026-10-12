/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

/// Package-level Hibernate mappings registered through `Configuration#addPackage`.
@NamedQuery(name = "Person.olderThan", query = "select p.name from Person p where p.age > :age order by p.name")
package org_hibernate_orm.hibernate_core.packaged;

import org.hibernate.annotations.NamedQuery;
