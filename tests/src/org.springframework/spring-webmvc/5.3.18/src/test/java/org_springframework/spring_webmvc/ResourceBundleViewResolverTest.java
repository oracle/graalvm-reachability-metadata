/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.AbstractUrlBasedView;
import org.springframework.web.servlet.view.InternalResourceView;
import org.springframework.web.servlet.view.ResourceBundleViewResolver;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceBundleViewResolverTest {

    @Test
    void resolvesViewDefinitionFromClasspathResourceBundle() throws Exception {
        GenericWebApplicationContext applicationContext = new GenericWebApplicationContext();
        ResourceBundleViewResolver resolver = new ResourceBundleViewResolver();
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.refresh();

        try {
            resolver.setApplicationContext(applicationContext);
            resolver.setBasename("org_springframework.spring_webmvc.resource_bundle_views");
            resolver.setBundleClassLoader(getClass().getClassLoader());

            View view = resolver.resolveViewName("home", Locale.ENGLISH);

            assertThat(view).isInstanceOf(InternalResourceView.class);
            assertThat(((AbstractUrlBasedView) view).getUrl()).isEqualTo("/WEB-INF/home.jsp");
        }
        finally {
            resolver.destroy();
            applicationContext.close();
        }
    }
}
