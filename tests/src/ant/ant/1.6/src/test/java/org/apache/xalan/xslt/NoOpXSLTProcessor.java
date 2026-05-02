/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.xalan.xslt;

public class NoOpXSLTProcessor implements XSLTProcessor {
    @Override
    public void setStylesheetParam(String name, String value) {
        // Accept stylesheet parameters used by the deprecated Ant Xalan liaison.
    }

    @Override
    public void process(
            XSLTInputSource input,
            XSLTInputSource stylesheet,
            XSLTResultTarget result) {
        // The XSLTProcess coverage test only instantiates the liaison.
    }
}
