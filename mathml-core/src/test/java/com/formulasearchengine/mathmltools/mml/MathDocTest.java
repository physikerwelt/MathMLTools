package com.formulasearchengine.mathmltools.mml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.junit.jupiter.api.Test;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

class MathDocTest {

    @Test
    void getXsdValidator() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        final DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        final XSImplementation impl = (XSImplementation) registry.getDOMImplementation("XS-Loader");
        XSLoader loader = impl.createXSLoader(null);
        XSModel xsd = loader.load(MathDoc.getMathMLSchema());
        assertEquals(2,xsd.getNamespaces().getLength());
    }
}
