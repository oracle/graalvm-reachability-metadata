/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_servlet_jsp_jstl.jakarta_servlet_jsp_jstl_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.ResourceBundle;

import jakarta.servlet.jsp.PageContext;
import jakarta.servlet.jsp.jstl.core.Config;
import jakarta.servlet.jsp.jstl.fmt.LocaleSupport;
import jakarta.servlet.jsp.jstl.fmt.LocalizationContext;
import org.junit.jupiter.api.Test;

public class LocaleSupportTest {
    private static final String NAMED_MESSAGES_BUNDLE = "jakarta_servlet_jsp_jstl.jakarta_servlet_jsp_jstl_api"
            + ".LocaleSupportTest$NamedLocaleMessages";
    private static final String ROOT_MESSAGES_BUNDLE = "jakarta_servlet_jsp_jstl.jakarta_servlet_jsp_jstl_api"
            + ".LocaleSupportTest$RootMessages";

    @Test
    void localizationContextAndLocaleSupportResolveAndFormatMessages() {
        Jakarta_servlet_jsp_jstl_apiTest.TestPageContext pageContext =
                new Jakarta_servlet_jsp_jstl_apiTest.TestPageContext();
        ResourceBundle bundle = new MessagesBundle();
        LocalizationContext context = new LocalizationContext(bundle, Locale.US);

        Config.set(pageContext, Config.FMT_LOCALIZATION_CONTEXT, context, PageContext.PAGE_SCOPE);

        assertThat(context.getResourceBundle()).isSameAs(bundle);
        assertThat(context.getLocale()).isEqualTo(Locale.US);
        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "plain")).isEqualTo("Plain text");
        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "welcome", new Object[] {"Ada"}))
                .isEqualTo("Welcome Ada");
        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "missing")).isEqualTo("???missing???");
        assertThat(new LocalizationContext().getResourceBundle()).isNull();
        assertThat(new LocalizationContext(bundle).getLocale()).isNull();
    }

    @Test
    void localeSupportLoadsBundleByBasenameAndAppliesConfiguredLocale() {
        Jakarta_servlet_jsp_jstl_apiTest.TestPageContext pageContext =
                new Jakarta_servlet_jsp_jstl_apiTest.TestPageContext();

        Config.set(pageContext, Config.FMT_LOCALE, "en-US", PageContext.PAGE_SCOPE);

        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "salutation", new Object[] {"Ada"},
                NAMED_MESSAGES_BUNDLE)).isEqualTo("Howdy Ada");
        assertThat(pageContext.getResponse().getLocale()).isEqualTo(Locale.US);
    }

    @Test
    void loadsRootBundleWhenConfiguredLocaleDoesNotMatchAvailableBundles() {
        Jakarta_servlet_jsp_jstl_apiTest.TestPageContext pageContext =
                new Jakarta_servlet_jsp_jstl_apiTest.TestPageContext();

        Config.set(pageContext, Config.FMT_LOCALE, "zz-ZZ", PageContext.PAGE_SCOPE);

        assertThat(LocaleSupport.getLocalizedMessage(pageContext, "rootOnly", ROOT_MESSAGES_BUNDLE))
                .isEqualTo("Resolved from the root bundle");
    }

    private static final class MessagesBundle extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"plain", "Plain text"},
                    {"welcome", "Welcome {0}"}
            };
        }
    }

    public static final class NamedLocaleMessages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"salutation", "Hello {0}"}
            };
        }
    }

    public static final class NamedLocaleMessages_en_US extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"salutation", "Howdy {0}"}
            };
        }
    }

    public static final class RootMessages extends ListResourceBundle {
        @Override
        protected Object[][] getContents() {
            return new Object[][] {
                    {"rootOnly", "Resolved from the root bundle"}
            };
        }
    }
}
