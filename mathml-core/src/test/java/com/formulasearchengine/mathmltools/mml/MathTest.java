package com.formulasearchengine.mathmltools.mml;

import org.apache.commons.lang3.NotImplementedException;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class MathTest {
    public static final String SIMPLE_WITH_DOCTYPE = "<!DOCTYPE math PUBLIC \"-//W3C//DTD MATHML 3.0 Transitional//EN\" \n"
            + "     \"http://www.w3.org/Math/DTD/mathml3/mathml3.dtd\">\n"
            + "<math xmlns=\"http://www.w3.org/1998/Math/MathML\">\n"
            + "     <ci>some content 1</ci>\n"
            + "</math>";

    public static final String TEST_DIR = "com/formulasearchengine/mathmltools/mml/tests/";

    static public String getFileContents(String fname) throws IOException {
        try (InputStream is = MathTest.class.getClassLoader().getResourceAsStream(fname)) {
            final Scanner s = new Scanner(is, "UTF-8");
            //Stupid scanner tricks to read the entire file as one token
            s.useDelimiter("\\A");
            return s.hasNext() ? s.next() : "";
        }
    }

    @Test
    void isValid() throws Exception {
        final String sampleMML = getFileContents(TEST_DIR + "Emc2.xml");
        MathDoc math = new MathDoc(sampleMML);
        assertThat(math.getValidationProblems().spliterator().getExactSizeIfKnown(), is(equalTo(0L)));
        //assertTrue(math.isValid());
    }

    @ParameterizedTest()
    @ValueSource(strings = {
            SIMPLE_WITH_DOCTYPE,
            "<math/>",
            "<math><mi>a</mi></math>"})
    void noWhitespaceTests(String tag) throws Exception {
        new MathDoc(tag);
    }

    @Test
    void EmcTest() throws IOException, ParserConfigurationException, SAXException {
        final String sampleMML = getFileContents(TEST_DIR + "Emc2.xml");
        new MathDoc(sampleMML);
    }

    @Test
    void altTest() throws IOException, ParserConfigurationException, SAXException {
        final String sampleMML = getFileContents(TEST_DIR + "measurable-space.xml");
        final String fixed = MathDoc.tryFixHeader(sampleMML);
        final MathDoc math = new MathDoc(fixed);
        math.changeTeXAnnotation("asdf");
        assertThat(math.toString(), new StringContains("alttext=\"asdf\""));
        assertThat(math.toString(), StringContains.containsString("asdf</annotation>"));
    }

    @Test
    void cdRewriter() throws IOException, ParserConfigurationException, SAXException {
        final String sampleMML = getFileContents(TEST_DIR + "Van_der_Waerden.xml");
        final String fixed = MathDoc.tryFixHeader(sampleMML);
        final MathDoc math = new MathDoc(fixed);
        math.fixGoldCd();
        assertThat(math.toString(), new StringContains("cd=\"wikidata\""));
    }

    @Test
    void latexMLMacroExtracter() throws IOException, ParserConfigurationException, SAXException {
        final String sampleMML = getFileContents(TEST_DIR + "measurable-space.xml");
        final String fixed = MathDoc.tryFixHeader(sampleMML);
        final MathDoc math = new MathDoc(fixed);
        math.fixGoldCd();
    }

    @Test
    void altTestFail() throws IOException, ParserConfigurationException, SAXException {
        final MathDoc math = new MathDoc(SIMPLE_WITH_DOCTYPE);
        assertThrows(NotImplementedException.class, () -> math.changeTeXAnnotation("asdf"));
    }

    @Test()
    void testToString() throws TransformerException {
        final MathDoc math = mock(MathDoc.class);
        doThrow(new TransformerException("test")).when(math).serializeDom();
        when(math.toString()).thenCallRealMethod();
        try {
            math.toString();
        } catch (Exception e) {
            assertEquals("test", e.getMessage());
        }
    }


    @ParameterizedTest()
    @ValueSource(strings = {
            "<!DOCTYPE math PUBLIC \"-//W3C//DTD MATHML 3.0 Transitional//EN\" \n"
                    + "     \"http://www.w3.org/Math/DTD/mathml3/mathml3.dtd\">\n"
                    + "<?xml version=\"1.0\"?>"
                    + "<math xmlns=\"http://www.w3.org/1998/Math/MathML\">\n"
                    + "     <ci>some content 1</ci>\n"
                    + "</math>",
            "<ns:math/>",
            "<math><XMLDocument >a</XMLDocument></math>"})
    void failingExamples(String tag) {
        assertThrows(Exception.class, () -> new MathDoc(tag));
    }
}

/*
Isolating test for debugging purposes
    @Test
    public void isolatedTest() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setValidating(true);
        dbf.setFeature("http://xml.org/sax/features/validation", true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        Input.Builder builder = Input.fromString(
                "<!DOCTYPE math PUBLIC \"-//W3C//DTD MATHML 3.0 Transitional//EN\" \n"
                        + "     \"http://www.w3.org/Math/DTD/mathml3/mathml3.dtd\">\n"
                        + "<math xmlns=\"http://www.w3.org/1998/Math/MathML\">\n"
                        + "     <ci>some content 1</ci>\n"
                        + "</math>");

        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver(new PartialLocalEntityResolver());
        db.parse(toInputSource(builder.build()));
    }
 */