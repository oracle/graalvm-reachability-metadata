/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static com.github.tomakehurst.wiremock.http.RequestMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.admin.AdminRoutes;
import com.github.tomakehurst.wiremock.admin.AdminTask;
import com.github.tomakehurst.wiremock.admin.RequestSpec;
import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.common.url.PathParams;
import com.github.tomakehurst.wiremock.core.Admin;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AdminRoutesInnerRouteBuilderTest {
    @Test
    void adminApiExtensionRegistersTaskFromTaskClass() {
        AdminRoutes routes = AdminRoutes.forServer(List.of(new ClassBasedRouteExtension()), null);

        AdminTask adminTask = routes.taskFor(GET, "/extension/class-based-route");
        RequestSpec requestSpec = routes.requestSpecForTask(ClassBasedRouteTask.class);

        assertThat(adminTask).isInstanceOf(ClassBasedRouteTask.class);
        assertThat(requestSpec.method()).isEqualTo(GET);
        assertThat(requestSpec.path()).isEqualTo("/extension/class-based-route");
    }

    public static class ClassBasedRouteExtension implements AdminApiExtension {
        @Override
        public String getName() {
            return "class-based-route-extension";
        }

        @Override
        public void contributeAdminApiRoutes(Router router) {
            router.add(GET, "/extension/class-based-route", ClassBasedRouteTask.class);
        }
    }

    public static class ClassBasedRouteTask implements AdminTask {
        @Override
        public ResponseDefinition execute(Admin admin, ServeEvent serveEvent, PathParams pathParams) {
            return new ResponseDefinition(200, "class-based-route");
        }
    }
}
