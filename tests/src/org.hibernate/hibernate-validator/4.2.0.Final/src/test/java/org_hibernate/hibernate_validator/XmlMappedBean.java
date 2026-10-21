/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_validator;

public class XmlMappedBean {
    private final String code;

    public XmlMappedBean(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return true;
    }
}
