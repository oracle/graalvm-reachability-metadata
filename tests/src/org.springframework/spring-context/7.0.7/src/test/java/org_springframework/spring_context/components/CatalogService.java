/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private CatalogRepository repository;
    private CatalogSettings settings;

    @Autowired
    public void configure(CatalogRepository repository, CatalogSettings settings) {
        this.repository = repository;
        this.settings = settings;
    }

    public String describe() {
        return settings.region() + ":" + repository.lookup();
    }
}
