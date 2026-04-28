/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.python.core;

public class PyComplex {
    private final PyFloat real;
    private final PyFloat imag;

    public PyComplex(PyFloat real, PyFloat imag) {
        this.real = real;
        this.imag = imag;
    }

    public PyFloat getReal() {
        return real;
    }

    public PyFloat getImag() {
        return imag;
    }
}
