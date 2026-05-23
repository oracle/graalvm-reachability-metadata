/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.util.ListResourceBundle;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.view.ResourceBundleViewResolver;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
public class ResourceBundleViewResolverTest {
    private static final String BUNDLE_BASENAME =
            "org_springframework.spring_webmvc.ResourceBundleViewResolverTest$ViewDefinitions";

    @Test
    void resolveViewNameLoadsViewDefinitionsFromResourceBundle() throws Exception {
        GenericWebApplicationContext applicationContext = new GenericWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.refresh();

        ResourceBundleViewResolver resolver = new ResourceBundleViewResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setBundleClassLoader(ResourceBundleViewResolverTest.class.getClassLoader());
        resolver.setBasename(BUNDLE_BASENAME);

        try {
            View view = resolver.resolveViewName("sampleRedirect", Locale.ENGLISH);

            assertThat(view).isInstanceOf(RedirectView.class);
            assertThat(((RedirectView) view).getUrl()).isEqualTo("/resource-bundle-target");
        } finally {
            resolver.destroy();
            applicationContext.close();
        }
    }

    public static class ViewDefinitions extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"sampleRedirect.(class)", RedirectView.class.getName()},
                    {"sampleRedirect.url", "/resource-bundle-target"}
            };
        }
    }
}
