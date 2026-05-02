/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.kvisco.xsl;

import java.io.Writer;

public class XSLProcessor {
    public String getProperty(String name) {
        return null;
    }

    public void setProperty(String name, String value) {
        // Accept stylesheet parameters used by the deprecated Ant XSLP liaison.
    }

    public void process(String inputFile, XSLStylesheet stylesheet, Writer writer) {
        // The XSLTProcess coverage test only instantiates the liaison.
    }
}
