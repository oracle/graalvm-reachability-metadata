/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package aQute.bnd.osgi;

public class Annotation {
    private final java.lang.annotation.Annotation annotation;

    public Annotation(java.lang.annotation.Annotation annotation) {
        this.annotation = annotation;
    }

    public java.lang.annotation.Annotation getAnnotation() {
        return annotation;
    }
}
