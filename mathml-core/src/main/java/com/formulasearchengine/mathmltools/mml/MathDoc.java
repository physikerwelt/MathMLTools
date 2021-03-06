package com.formulasearchengine.mathmltools.mml;

import static com.formulasearchengine.mathmltools.helper.XMLHelper.NS_MATHML;
import static com.formulasearchengine.mathmltools.helper.XMLHelper.getElementsB;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.formulasearchengine.mathmltools.helper.XMLHelper;
import com.formulasearchengine.mathmltools.io.XmlDocumentReader;
import com.formulasearchengine.mathmltools.io.XmlDocumentWriter;
import com.formulasearchengine.mathmltools.utils.mml.CSymbol;
import com.formulasearchengine.mathmltools.xml.PartialLocalEntityResolver;

import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmlunit.builder.Input;
import org.xmlunit.util.IterableNodeList;
import org.xmlunit.validation.JAXPValidator;
import org.xmlunit.validation.Languages;
import org.xmlunit.validation.ValidationProblem;
import org.xmlunit.validation.ValidationResult;
import org.xmlunit.validation.Validator;

public class MathDoc {
    private static final Logger log = LogManager.getLogger("Math");

    private static final String PREFIX_PLACEHOLDER = "!!PREFIX!!";

    private static final String NL = System.lineSeparator();

    private static final String DOCTYPE =
            "<!DOCTYPE math PUBLIC \"-//W3C//DTD MATHML 3.0 Transitional//EN\"" + NL
                    + "     \"http://www.w3.org/Math/DTD/mathml3/mathml3.dtd\">" + NL;

    private static final String DOCTYPE_PREFIX =
            "<!DOCTYPE !!PREFIX!!:math\n"
                    + "     PUBLIC \"-//W3C//DTD MATHML 3.0 Transitional//EN\"" + NL
                    + "            \"http://www.w3.org/Math/DTD/mathml3/mathml3.dtd\" [" + NL
                    + "     <!ENTITY % MATHML.prefixed \"INCLUDE\">" + NL
                    + "     <!ENTITY % MATHML.prefix \"!!PREFIX!!\">" + NL
                    + "]>" + NL;

    private static final String MATH_NS_BOUND = "xmlns:!!PREFIX!!=\"http://www.w3.org/1998/Math/MathML\"";

    private static final String MATH_NS_BOUND_PATTERN =
            "<(!!PREFIX!!:math[^>]*?)(.*xmlns:!!PREFIX!!\\s*=\\s*[\"']http://www.w3.org/1998/Math/MathML[\"'].*)?";

    private static final Pattern NAMESPACE_PREFIX_PATTERN_MATH =
            Pattern.compile("<(\\w+):math");

    private static final String MATHML3_XSD = "https://www.w3.org/Math/XMLSchema/mathml3/mathml3.xsd";
    private static final String APPLICATION_X_TEX = "application/x-tex";
    private static Validator v;

    private List<CSymbol> cSymbols = null;
    private List<CIdentifier> cIdentifiers = null;

    private Document dom;

    private static DOMImplementationLS domImplLS;

    static {
        try {
            domImplLS = (DOMImplementationLS) DOMImplementationRegistry.newInstance().getDOMImplementation("LS");
        } catch (Exception e) {
            log.error("Cannot instantiate LSInput implementation", e);
        }
    }

    /**
     * Creates a MathDoc object based on the xml string input
     *
     * @param inputXMLString MML string
     * @throws IOException
     * @throws SAXException
     * @throws IllegalArgumentException
     */
    public MathDoc(String inputXMLString) throws IOException, SAXException, IllegalArgumentException {
        dom = XmlDocumentReader.parse(inputXMLString);
    }

    /**
     * Creates a MathDoc object based on the MML path
     *
     * @param path to MML file
     * @throws IOException
     * @throws SAXException
     * @throws IllegalArgumentException
     */
    public MathDoc(Path path) throws IOException, SAXException, IllegalArgumentException {
        dom = XmlDocumentReader.parse(path);
    }

    /**
     * Creates a MathDoc object based on the MML file
     *
     * @param file MML file
     * @throws IOException
     * @throws SAXException
     * @throws IllegalArgumentException
     */
    public MathDoc(File file) throws IOException, SAXException, IllegalArgumentException {
        dom = XmlDocumentReader.parse(file);
    }

    public MathDoc(Document dom) {
        this.dom = dom;
    }

    public static String fixingHeaderAndNS(String in) {
        StringBuffer input = new StringBuffer(in);

        // first delete xml declaration (it's not needed)
        XMLHelper.removeXmlDeclaration(input);

        // second delete DOCTYPE declaration (it's most likely missing or broken)
        XMLHelper.removeDoctype(input);

        // third try extracting NS prefix
        String prefix = extractNamespacePrefix(input);
        if (prefix != null) {
            fixMathElement(input, prefix);
            String docType = DOCTYPE_PREFIX.replaceAll(PREFIX_PLACEHOLDER, prefix);
            return docType + input;
        } else {
            return DOCTYPE + input;
        }
    }

    private static void fixMathElement(StringBuffer original, String nsPrefix) {
        String patternString = MATH_NS_BOUND_PATTERN.replaceAll(PREFIX_PLACEHOLDER, nsPrefix);
        Pattern p = Pattern.compile(patternString, Pattern.DOTALL);
        Matcher m = p.matcher(original);

        if (m.find()) {
            if (m.group(2) == null) {
                log.warn("Found prefix without namespace binding. Try fixing it...");
                String namespaceBound = MATH_NS_BOUND.replaceAll(PREFIX_PLACEHOLDER, nsPrefix);
                original.replace(m.start(1), m.end(1), m.group(1) + " " + namespaceBound);
            }
        }
    }

    public static String extractNamespacePrefix(StringBuffer rawXml) {
        Matcher m = NAMESPACE_PREFIX_PATTERN_MATH.matcher(rawXml);
        if (m.find()) {
            String prefix = m.group(1);
            log.debug("Found namespace prefix '{}' of 'math' element.", prefix);
            return prefix;
        }

        return null;
    }

    public static LSInput getMathMLSchema() {
        SchemaInput schemaInput = new SchemaInput().invoke();
        InputSource inputSource = schemaInput.getInputSource();
        final LSInput input = domImplLS.createLSInput();
        input.setByteStream(inputSource.getByteStream());
        input.setPublicId(inputSource.getPublicId());
        input.setSystemId(inputSource.getSystemId());
        return input;
    }

    private static Validator getXsdValidator() {
        if (v == null) {
            SchemaInput schemaInput = new SchemaInput().invoke();
            SchemaFactory schemaFactory = schemaInput.getSchemaFactory();
            InputSource inputSource = schemaInput.getInputSource();
            v = new JAXPValidator(Languages.W3C_XML_SCHEMA_NS_URI, schemaFactory);
            final StreamSource streamSource = new StreamSource(inputSource.getByteStream());
            streamSource.setPublicId(inputSource.getPublicId());
            streamSource.setSystemId(inputSource.getSystemId());
            v.setSchemaSource(streamSource);
        }
        return v;
    }

    /**
     * NOTE! Currently no new annotations are added
     *
     * @param newTeX
     */
    public void changeTeXAnnotation(String newTeX) {
        dom.getDocumentElement().setAttribute("alttext", newTeX);
        if (getAnnotationElements().getLength() > 0) {
            log.trace("Found annotation elements");
            for (Node node : new IterableNodeList(getAnnotationElements())) {
                if (node.getAttributes().getNamedItem("encoding").getNodeValue().equals(APPLICATION_X_TEX)) {
                    log.trace("Found annotation elements with encoding {}", APPLICATION_X_TEX);
                    node.setTextContent(newTeX);
                }
            }
        } else {
            throw new NotImplementedException("Implement no annotations case.");
        }
    }

    private NodeList getAnnotationElements() {
        return dom.getElementsByTagName("annotation");
    }

    Iterable<ValidationProblem> getValidationProblems() {
        ValidationResult result = getValidationResult();
        return result.getProblems();
    }

    private ValidationResult getValidationResult() {
        Validator v = getXsdValidator();
        return v.validateInstance(Input.fromDocument(dom).build());
    }

    @Override
    public String toString() {
        try {
            return XmlDocumentWriter.stringify(dom);
        } catch (TransformerException ioe) {
            log.error("Cannot stringify document.", ioe);
            return null;
        }
    }

    public void fixGoldCd() {
        getSymbolsFromCd("latexml").filter(n -> n.getCName().startsWith("Q")).forEach(cSymbol -> {
            log.trace("Processing symbol {}", cSymbol);
            cSymbol.setCd("wikidata");
        });

    }

    private Stream<CSymbol> getSymbolsFromCd(String cd) {
        return getCSymbols().stream().filter(n -> n.getCd().equals(cd));
    }

    public List<CSymbol> getCSymbols() {
        if (cSymbols == null) {
            final IterableNodeList nodeList = new IterableNodeList(dom.getElementsByTagName("csymbol"));
            cSymbols = new ArrayList<>();
            nodeList.forEach(n -> cSymbols.add(new CSymbol((Element) n)));
        }
        return cSymbols;
    }

    public List<CIdentifier> getIdentifiers() {
        if (cIdentifiers == null) {
            final IterableNodeList nodeList;
            nodeList = getXNodes("//m:ci");
            cIdentifiers = new ArrayList<>();
            int i = 0;
            for (Node node : nodeList) {
                cIdentifiers.add(new CIdentifier((Element) node, i));
                i++;
            }
        }
        return cIdentifiers;
    }

    private IterableNodeList getXNodes(String xPath) {
        XPath xpath = XMLHelper.namespaceAwareXpath("m", NS_MATHML);
        try {
            final XPathExpression pattern = xpath.compile(xPath);
            final NodeList elements = getElementsB(dom, pattern);
            return new IterableNodeList(elements);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    public Document getDom() {
        return dom;
    }

    /**
     * Highlights consecutive occurrences of identifiers.
     *
     * @param hashes   list of content identifier hashes to highlight
     * @param backward if true the first identifier is searched from the end of the expression
     */
    public void highlightConsecutiveIdentifiers(List<Integer> hashes, boolean backward) {
        final int startPos = highlightFirstIdentifier(hashes.get(0), backward);
        if (startPos >= 0) {
            highlightRemainingIdentifiers(hashes.subList(1, hashes.size()), startPos);
        }
    }

    private void highlightRemainingIdentifiers(List<Integer> hashes, int pos) {
        final List<CIdentifier> identifiers = getIdentifiers();
        for (Integer curHash : hashes) {
            final CIdentifier curIdent = identifiers.get(pos + 1);
            if (curHash == curIdent.hashCode()) {
                highlightIdentifier(curIdent);
                pos++;
            } else {
                return;
            }
        }
    }

    private int highlightFirstIdentifier(int hash, boolean backward) {
        Stream<CIdentifier> identifiers = getIdentifiers().stream();
        if (backward) {
            identifiers = identifiers.sorted(Collections.reverseOrder());
        }
        return identifiers
                .filter(c -> c.hashCode() == hash)
                .findFirst()
                .map(c -> {
                    highlightIdentifier(c);
                    return c.getOrdinal();
                })
                .orElse(-1);
    }


    private void highlightIdentifier(CIdentifier identifier) {
        try {
            final Element identifierPresentation = identifier.getPresentation();
            if (identifierPresentation.hasAttribute("class")) {
                log.warn("Presentation node " + identifier.getXref() + " has already class attribute. Cannot highlight!");
            } else {
                identifierPresentation.setAttribute("class", "highlightedIdentifier");
            }
        } catch (XPathExpressionException e) {
            log.warn("Can not highlight presentation node " + identifier.getXref());
            log.warn(e);
        }
    }

    private static class SchemaInput {
        private SchemaFactory schemaFactory;
        private InputSource inputSource;

        SchemaFactory getSchemaFactory() {
            return schemaFactory;
        }

        InputSource getInputSource() {
            return inputSource;
        }

        SchemaInput invoke() {
            schemaFactory = SchemaFactory.newInstance(Languages.W3C_XML_SCHEMA_NS_URI);
            final PartialLocalEntityResolver resolver = new PartialLocalEntityResolver();
            schemaFactory.setResourceResolver(resolver);
            inputSource = resolver.resolveEntity("math", MATHML3_XSD);
            assert inputSource != null;
            return this;
        }
    }
}
