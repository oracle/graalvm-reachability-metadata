/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' application block-helper subsystem for a role-aware dashboard.
public class AccessControlledDashboardTest {
    @Test
    @Timeout(60)
    public void rendersDashboardActionsForUserRole() throws IOException {
        Handlebars handlebars = new Handlebars();
        handlebars.registerHelper("canEdit", new CanEditHelper());
        Template template = handlebars.compileInline(
                "<main><h1>{{title}}</h1>"
                        + "{{#canEdit role required=\"editor\"}}"
                        + "<button data-action=\"edit\">Edit report</button>"
                        + "{{else}}<p class=\"notice\">View only</p>{{/canEdit}}</main>");

        String editorDashboard = template.apply(Context.newBuilder(Map.of(
                "title", "Quarterly report",
                "role", "editor")).build());
        String viewerDashboard = template.apply(Context.newBuilder(Map.of(
                "title", "Quarterly report",
                "role", "viewer")).build());

        assertEquals(
                "<main><h1>Quarterly report</h1>"
                        + "<button data-action=\"edit\">Edit report</button></main>",
                editorDashboard);
        assertEquals(
                "<main><h1>Quarterly report</h1>"
                        + "<p class=\"notice\">View only</p></main>",
                viewerDashboard);
    }

    private static final class CanEditHelper implements Helper<String> {
        @Override
        public Object apply(String role, Options options) throws IOException {
            String requiredRole = options.hash("required", "editor");
            return requiredRole.equals(role) ? options.fn() : options.inverse();
        }
    }
}
