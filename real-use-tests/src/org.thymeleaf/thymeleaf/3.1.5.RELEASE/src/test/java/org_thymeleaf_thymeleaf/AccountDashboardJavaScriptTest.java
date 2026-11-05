/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf_thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Thymeleaf's JavaScript inlining and serialization subsystem for an account dashboard.
public class AccountDashboardJavaScriptTest {
    @Test
    public void rendersSerializedAccountStateForDashboardScript() {
        TemplateEngine templateEngine = newTemplateEngine();
        Context context = new Context();
        context.setVariable("account", new Account("Ada", "ada@example.test", true));
        context.setVariable("permissions", List.of("reports:read", "reports:export"));

        String rendered = templateEngine.process(DASHBOARD_TEMPLATE, context);

        assertTrue(rendered.contains("const account = {\"displayName\":\"Ada\",\"email\":\"ada@example.test\",\"active\":true};"));
        assertTrue(rendered.contains("const permissions = [\"reports:read\",\"reports:export\"];"));
        assertTrue(rendered.contains("const dashboardTitle = \"Account dashboard\";"));
        assertTrue(rendered.contains("<h1>Account dashboard</h1>"));
    }

    private static TemplateEngine newTemplateEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);

        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return templateEngine;
    }

    private record Account(String displayName, String email, boolean active) {
    }

    private static final String DASHBOARD_TEMPLATE = """
            <!doctype html>
            <html xmlns:th="http://www.thymeleaf.org">
            <head><title th:text="'Account dashboard'">Dashboard</title></head>
            <body>
            <h1 th:text="'Account dashboard'">Dashboard</h1>
            <script th:inline="javascript">
            const account = /*[[${account}]]*/ {};
            const permissions = /*[[${permissions}]]*/ [];
            const dashboardTitle = /*[['Account dashboard']]*/ "";
            </script>
            </body>
            </html>
            """;
}
