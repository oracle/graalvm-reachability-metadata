/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jdom.adapters;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.jdom.input.BuilderErrorHandler;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Test-compiled copy of JDOM beta 8's Xerces adapter so dynamic-access stack
 * frames include line numbers that can be matched against JaCoCo coverage.
 */
public class XercesDOMAdapter extends AbstractDOMAdapter {
    @Override
    public Document getDocument(InputStream inputStream, boolean validate) throws IOException {
        try {
            Class<?> parserClass = Class.forName("org.apache.xerces.parsers.DOMParser");
            Object parser = parserClass.newInstance();

            Method setFeature = parserClass.getMethod(
                    "setFeature",
                    new Class<?>[] {String.class, boolean.class});
            setFeature.invoke(parser, new Object[] {
                    "http://xml.org/sax/features/validation",
                    Boolean.valueOf(validate)});
            setFeature.invoke(parser, new Object[] {
                    "http://xml.org/sax/features/namespaces",
                    Boolean.TRUE});

            if (validate) {
                Method setErrorHandler = parserClass.getMethod(
                        "setErrorHandler",
                        new Class<?>[] {ErrorHandler.class});
                setErrorHandler.invoke(parser, new Object[] {new BuilderErrorHandler()});
            }

            Method parse = parserClass.getMethod(
                    "parse",
                    new Class<?>[] {InputSource.class});
            parse.invoke(parser, new Object[] {new InputSource(inputStream)});

            Method getDocument = parserClass.getMethod("getDocument", null);
            return (Document) getDocument.invoke(parser, null);
        } catch (InvocationTargetException exception) {
            Throwable targetException = exception.getTargetException();
            if (targetException instanceof SAXParseException) {
                SAXParseException parseException = (SAXParseException) targetException;
                throw new IOException("Error on line "
                        + parseException.getLineNumber()
                        + " of XML document: "
                        + parseException.getMessage());
            }
            throw new IOException(targetException.getMessage());
        } catch (Exception exception) {
            throw new IOException(exception.getClass().getName() + ": " + exception.getMessage());
        }
    }

    @Override
    public Document createDocument() throws IOException {
        try {
            return (Document) Class.forName("org.apache.xerces.dom.DocumentImpl").newInstance();
        } catch (Exception exception) {
            throw new IOException(exception.getClass().getName() + ": " + exception.getMessage());
        }
    }
}
