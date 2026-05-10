/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.ResourceBundleViewResolver;

public class ResourceBundleViewResolverTest {
    private static final String BUNDLE_BASENAME = "org_springframework.spring_webmvc.resourceBundleViews";

    @Test
    void resolveMissingViewLoadsConfiguredResourceBundle() throws Exception {
        StaticWebApplicationContext applicationContext = new StaticWebApplicationContext();
        applicationContext.setServletContext(new MockServletContext());
        applicationContext.refresh();

        ResourceBundleViewResolver resolver = new ResourceBundleViewResolver();
        resolver.setBasename(BUNDLE_BASENAME);
        resolver.setBundleClassLoader(ResourceBundleViewResolverTest.class.getClassLoader());
        resolver.setApplicationContext(applicationContext);

        try {
            View view = resolver.resolveViewName("missingView", Locale.US);

            assertThat(view).isNull();
        } finally {
            resolver.destroy();
            applicationContext.close();
        }
    }
}
