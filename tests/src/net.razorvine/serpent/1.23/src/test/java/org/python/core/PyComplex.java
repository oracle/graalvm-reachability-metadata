/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.python.core;

public final class PyComplex {
    private final PyFloat real;
    private final PyFloat imaginary;

    public PyComplex(double real, double imaginary) {
        this.real = new PyFloat(real);
        this.imaginary = new PyFloat(imaginary);
    }

    public PyFloat getReal() {
        return real;
    }

    public PyFloat getImag() {
        return imaginary;
    }
}
