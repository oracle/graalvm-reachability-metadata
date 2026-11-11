/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context.components;

import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    public String lookup() {
        return "books";
    }
}
