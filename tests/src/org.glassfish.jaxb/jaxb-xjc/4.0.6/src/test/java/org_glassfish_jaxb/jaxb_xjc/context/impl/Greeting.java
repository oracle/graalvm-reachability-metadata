/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_xjc.context.impl;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "greeting")
public class Greeting {
    public String text;
}
