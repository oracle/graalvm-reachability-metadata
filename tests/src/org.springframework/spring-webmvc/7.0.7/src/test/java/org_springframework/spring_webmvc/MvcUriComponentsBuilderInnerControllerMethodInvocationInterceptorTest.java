/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MvcUriComponentsBuilderInnerControllerMethodInvocationInterceptorTest {
    @Test
    void buildsLinkFromInterfaceControllerAndInterfaceReturnValue() {
        CatalogController controller = MvcUriComponentsBuilder.on(CatalogController.class);
        Item result = controller.item("spring", "details");

        String uri = MvcUriComponentsBuilder.fromMethodCall(
                UriComponentsBuilder.fromUriString("https://example.org/api"), result)
                .build().toUriString();

        assertEquals("https://example.org/api/catalog/spring?view=details", uri);
    }

    @Test
    void remembersArgumentsForRepeatedVoidMethodInvocations() {
        CatalogController controller = MvcUriComponentsBuilder.controller(CatalogController.class);
        MvcUriComponentsBuilder builder = MvcUriComponentsBuilder.relativeTo(
                UriComponentsBuilder.fromPath("/api"));

        controller.remove("first");
        assertEquals("/api/catalog/first/remove", builder.withMethodCall(controller).build().toUriString());
        controller.remove("second");
        assertEquals("/api/catalog/second/remove", builder.withMethodCall(controller).build().toUriString());
    }

    @RequestMapping("/catalog")
    public interface CatalogController {
        @GetMapping("/{name}")
        Item item(@PathVariable("name") String name, @RequestParam("view") String view);

        @GetMapping("/{name}/remove")
        void remove(@PathVariable("name") String name);
    }

    public interface Item {
        String name();
    }
}
