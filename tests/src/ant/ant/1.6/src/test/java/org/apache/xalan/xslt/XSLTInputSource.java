/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.xalan.xslt;

import java.io.InputStream;

import org.xml.sax.InputSource;

public class XSLTInputSource extends InputSource {
    public XSLTInputSource(InputStream inputStream) {
        super(inputStream);
    }
}
