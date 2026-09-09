/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jvnet_jaxb.jaxb_plugins_runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.jvnet.jaxb.locator.AbstractObjectLocator;
import org.jvnet.jaxb.locator.ObjectLocator;
import org.junit.jupiter.api.Test;

public class AbstractObjectLocatorTest {
    @Test
    void formatsLocalizedMessageAndBuildsLocatorPath() {
        LocalizedObjectLocator root = new LocalizedObjectLocator(null, "catalog", "root");
        ObjectLocator title = root.property("title", "Native Image");

        assertThat(root.getMessage()).isEqualTo("Locator root: catalog.");
        assertThat(root.getPath()).containsExactly(root);
        assertThat(title.getPath()).containsExactly(root, title);
        assertThat(title.getPathAsString()).isEqualTo("root.title");
    }
}

final class LocalizedObjectLocator extends AbstractObjectLocator {
    private final String step;

    LocalizedObjectLocator(ObjectLocator parentLocator, Object object, String step) {
        super(parentLocator, object);
        this.step = step;
    }

    @Override
    public Object[] getMessageParameters() {
        return new Object[] {getObject(), step};
    }

    @Override
    protected String getDefaultMessage() {
        return "Locator " + step + ": " + getObject();
    }

    @Override
    protected String getStepAsString() {
        return step;
    }
}
