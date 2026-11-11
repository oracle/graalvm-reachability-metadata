/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

@Controller
public class CatalogController {
    private CatalogService service;

    @Autowired
    public void setService(CatalogService service) {
        this.service = service;
    }

    public String render() {
        return "catalog=" + service.describe();
    }
}
