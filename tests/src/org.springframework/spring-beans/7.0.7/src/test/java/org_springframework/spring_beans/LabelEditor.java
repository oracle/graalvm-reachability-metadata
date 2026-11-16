/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.beans.PropertyEditorSupport;

public class LabelEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) {
        setValue(new Label(text));
    }
}
