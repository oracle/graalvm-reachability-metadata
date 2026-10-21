/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_validator;

import java.util.Locale;
import javax.validation.MessageInterpolator;

public class XmlMessageInterpolator implements MessageInterpolator {
    @Override
    public String interpolate(String messageTemplate, Context context) {
        return "xml:" + messageTemplate;
    }

    @Override
    public String interpolate(String messageTemplate, Context context, Locale locale) {
        return "xml:" + messageTemplate;
    }
}
