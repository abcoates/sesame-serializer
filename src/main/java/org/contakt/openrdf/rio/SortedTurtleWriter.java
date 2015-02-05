package org.contakt.openrdf.rio;

import info.aduna.io.IndentingWriter;
import org.openrdf.model.*;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.helpers.RDFWriterBase;

import javax.xml.namespace.QName;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.*;

/**
 * Equivalent to Sesame's built-in Turtle writer, but the triples are sorted into a consistent order.
 * In order to do the sorting, it must be possible to load all of the RDF statements into memory.
 * NOTE: comments are suppressed, as there isn't a clear way to sort them along with triples.
 */
public class SortedTurtleWriter extends RDFWriterBase {

    /** XML Schema namespace URI. */
    public static final String XML_SCHEMA_NS_URI = "http://www.w3.org/2001/XMLSchema#";

    /** RDF namespace URI. */
    public static final String RDF_NS_URI = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    /** RDF Schema (RDFS) namespace URI. */
    public static final String RDFS_NS_URI = "http://www.w3.org/2000/01/rdf-schema#";

    /** OWL namespace URI. */
    public static final String OWL_NS_URI = "http://www.w3.org/2002/07/owl#";

    /** rdf:type ('a') URL */
    private static final URI rdfType = new URIImpl(RDF_NS_URI + "type");

    /** rdfs:label URL */
    private static final URI rdfsLabel = new URIImpl(RDFS_NS_URI + "label");

    /** rdfs:comment URL */
    private static final URI rdfsComment = new URIImpl(RDFS_NS_URI + "comment");

    /** rdfs:subClassOf URL */
    private static final URI rdfsSubClassOf = new URIImpl(RDFS_NS_URI + "subClassOf");

    /** rdfs:subPropertyOf URL */
    private static final URI rdfsSubPropertyOf = new URIImpl(RDFS_NS_URI + "subPropertyOf");

    /** owl:Ontology URL */
    private static final URI owlOntology = new URIImpl(OWL_NS_URI + "Ontology");

    /** owl:sameAs URL */
    private static final URI owlSameAs = new URIImpl(OWL_NS_URI + "sameAs");

    /** Comparator for Sesame Value objects. */
    private class ValueComparator implements Comparator<Value> {
        @Override
        public int compare(Value value1, Value value2) {
            if (value1 == null) { throw new NullPointerException("cannot compare null to value"); }
            if (value2 == null) { throw new NullPointerException("cannot compare value to null"); }
            return value1.stringValue().compareTo(value2.stringValue());
        }
    }

    /** A list of RDF object values. */
    private class TurtleObjectList extends TreeSet<Value> {
        public TurtleObjectList() { super(new ValueComparator()); }
    }

    /** Comparator for Sesame URI objects. */
    private class URIComparator implements Comparator<URI> {
        @Override
        public int compare(URI uri1, URI uri2) {
            if (uri1 == null) { throw new NullPointerException("cannot compare null to URI"); }
            if (uri2 == null) { throw new NullPointerException("cannot compare URI to null"); }
            return uri1.stringValue().compareTo(uri2.stringValue());
        }
    }

    /** A map from predicate URIs to lists of object values. */
    private class TurtlePredicateObjectMap extends TreeMap<URI, TurtleObjectList> {
        public TurtlePredicateObjectMap() { super(new URIComparator()); }
    }

    /** A list of RDF URI values. */
    private class TurtlePredicateList extends TreeSet<URI> {
        public TurtlePredicateList() { super(new URIComparator()); }
    }

    /** Comparator for Sesame Resource objects. */
    private class ResourceComparator implements Comparator<Resource> {
        @Override
        public int compare(Resource resource1, Resource resource2) {
            if (resource1 == null) { throw new NullPointerException("cannot compare null to resource"); }
            if (resource2 == null) { throw new NullPointerException("cannot compare resource to null"); }
            return resource1.stringValue().compareTo(resource2.stringValue());
        }
    }

    /** A map from subject resources to predicate/object pairs. */
    private class TurtleSubjectPredicateObjectMap extends TreeMap<Resource, TurtlePredicateObjectMap> {
        public TurtleSubjectPredicateObjectMap() { super(new ResourceComparator()); }
    }

    /** A list of RDF resource values. */
    private class TurtleResourceList extends TreeSet<Resource> {
        public TurtleResourceList() { super(new ResourceComparator()); }
    }

    /** Comparator for Sesame BNode objects. */
    // TODO: give this a proper implementation that *does not* use 'stringValue'
    private class BNodeComparator implements Comparator<BNode> {
        @Override
        public int compare(BNode bnode1, BNode bnode2) {
            if (bnode1 == null) { throw new NullPointerException("cannot compare null to BNode"); }
            if (bnode2 == null) { throw new NullPointerException("cannot compare BNode to null"); }
            return bnode1.stringValue().compareTo(bnode2.stringValue());
        }
    }

    /** A list of RDF blank nodes. */
    private class TurtleBNodeList extends TreeSet<BNode> {
        public TurtleBNodeList() { super(new BNodeComparator()); }
    }

    /** Output stream for this Turtle writer. */
    private IndentingWriter output = null;

    /** Base URI for the Turtle output document. */
    private URI baseUri = null;

    /** List of subjects which are OWL ontologies, as they are rendered before other subjects. */
    private TurtleResourceList ontologies = null;

    /** List of blank nodes which are objects of statements. */
    private TurtleBNodeList objectBlankNodes = null;

    /** List of blank nodes which not the object of another statement. */
    private TurtleBNodeList globalBlankNodes = null;

    /** Hash map containing triple data. */
    private TurtleSubjectPredicateObjectMap tripleMap = null;

    /** Comparator for Strings that shorts longer strings first. */
    private class StringLengthComparator implements Comparator<String> {
        @Override
        public int compare(String str1, String str2) {
            if (str1 == null) { throw new NullPointerException("cannot compare null to String"); }
            if (str2 == null) { throw new NullPointerException("cannot compare String to null"); }
            if (str1.length() > str2.length()) {
                return -1;
            } else if (str1.length() < str2.length()) {
                return 1;
            } else { // if same length
                return str1.compareTo(str2);
            }
        }
    }

    /** A reverse namespace table with which returns the longest namespace URIs first.  Key is URI string, value is prefix string. */
    private class ReverseNamespaceTable extends TreeMap<String,String> {
        public ReverseNamespaceTable() { super(new StringLengthComparator()); }
    }

    /** Reverse namespace table used to map URIs to prefixes.  Key is URI string, value is prefix string. */
    private ReverseNamespaceTable reverseNamespaceTable = null;

    /**
     * Creates an RDFWriter instance that will write sorted Turtle to the supplied output stream.
     *
     * @param out The OutputStream to write the Turtle to.
     */
    public SortedTurtleWriter(OutputStream out) {
        assert out != null : "output stream cannot be null";
        this.output = new IndentingWriter(new OutputStreamWriter(out));
    }

    /**
     * Creates an RDFWriter instance that will write sorted Turtle to the supplied writer.
     *
     * @param writer The Writer to write the Turtle to.
     */
    public SortedTurtleWriter(Writer writer) {
        assert writer != null : "output writer cannot be null";
        this.output = new IndentingWriter(writer);
    }

    /**
     * Creates an RDFWriter instance that will write sorted Turtle to the supplied output stream.
     *
     * @param out The OutputStream to write the Turtle to.
     * @param baseUri The base URI for the Turtel, or null.
     * @param indent The indentation string to use when formatting the Turtle output.
     */
    public SortedTurtleWriter(OutputStream out, URI baseUri, String indent) {
        assert out != null : "output stream cannot be null";
        this.output = new IndentingWriter(new OutputStreamWriter(out));
        if (indent != null) { this.output.setIndentationString(indent); }
        this.baseUri = baseUri;
    }

    /**
     * Creates an RDFWriter instance that will write sorted Turtle to the supplied writer.
     *
     * @param writer The Writer to write the Turtle to.
     * @param baseUri The base URI for the Turtel, or null.
     * @param indent The indentation string to use when formatting the Turtle output, or null.
     */
    public SortedTurtleWriter(Writer writer, URI baseUri, String indent) {
        assert writer != null : "output writer cannot be null";
        this.output = new IndentingWriter(writer);
        if (indent != null) { this.output.setIndentationString(indent); }
        this.baseUri = baseUri;
    }

    /**
     * Gets the RDF format that this RDFWriter uses.
     */
    @Override
    public RDFFormat getRDFFormat() {
        return RDFFormat.TURTLE;
    }

    /**
     * Signals the start of the RDF data. This method is called before any data
     * is reported.
     *
     * @throws org.openrdf.rio.RDFHandlerException If the RDF handler has encountered an unrecoverable error.
     */
    @Override
    public void startRDF() throws RDFHandlerException {
        output.setIndentationLevel(0);
        namespaceTable = new TreeMap<String, String>();
        ontologies = new TurtleResourceList();
        objectBlankNodes = new TurtleBNodeList();
        globalBlankNodes = new TurtleBNodeList();
        tripleMap = new TurtleSubjectPredicateObjectMap();
    }

    /**
     * Adds a default namespace prefix to the namespace table, if no prefix has been defined.
     * @param namespaceUri The namespace URI.  Cannot be null.
     * @param defaultPrefix The default prefix to use, if no prefix is yet assigned.  Cannot be null.
     */
    private void addDefaultNamespacePrefixIfMissing(String namespaceUri, String defaultPrefix) {
        if ((namespaceUri != null) && (defaultPrefix != null)) {
            if (!namespaceTable.containsValue(namespaceUri)) {
                namespaceTable.put(defaultPrefix, namespaceUri);
            }
        }
    }

    /**
     * Signals the end of the RDF data. This method is called when all data has
     * been reported.
     *
     * @throws org.openrdf.rio.RDFHandlerException If the RDF handler has encountered an unrecoverable error.
     */
    @Override
    public void endRDF() throws RDFHandlerException {
        try {
            // Add default namespace prefixes, if they haven't yet been defined.  May fail if these prefixes have already been defined for different namespace URIs.
            addDefaultNamespacePrefixIfMissing(RDF_NS_URI, "rdf");
            addDefaultNamespacePrefixIfMissing(RDFS_NS_URI, "rdfs");
            addDefaultNamespacePrefixIfMissing(OWL_NS_URI, "owl");
            addDefaultNamespacePrefixIfMissing(XML_SCHEMA_NS_URI, "xs");

            // Create reverse namespace table.
            reverseNamespaceTable = new ReverseNamespaceTable();
            for (String prefix : namespaceTable.keySet()) {
                String uri = namespaceTable.get(prefix);
                reverseNamespaceTable.put(uri, prefix);
            }

            // Write the baseURI, if any.  Add comment version to support TopBraid Composer.
            if (baseUri != null) {
                output.write("# baseURI: " + baseUri); output.writeEOL();
                output.write("@base <" + baseUri + "> ."); output.writeEOL();
                output.writeEOL();
            }

            // Write out prefixes and namespaces URIs.
            if (namespaceTable.size() > 0) {
                TreeSet<String> prefixes = new TreeSet<String>(namespaceTable.keySet());
                for (String prefix : prefixes) {
                    output.write("@prefix " + prefix + ": <" + namespaceTable.get(prefix) + "> ."); output.writeEOL();
                }
                output.writeEOL();
            }

            // Write out subjects which are ontologies.
            // TODO: fix the following to handle blank nodes as well.
            for (Resource subject : ontologies) {
                if (!(subject instanceof BNode)) { // TODO: remove this suppression of blank nodes
                    writeSubjectTriples(output, subject);
                }
            }

            // Write out all other subjects (not ontologies).
            // TODO: fix the following to handle blank nodes as well.
            for (Resource subject : tripleMap.keySet()) {
                if (!ontologies.contains(subject)) {
                    if (!(subject instanceof BNode)) { // TODO: remove this suppression of blank nodes
                        writeSubjectTriples(output, subject);
                    }
                }
            }

            // TODO: deal with blank nodes that are subjects or objects

            output.flush();
        } catch (Throwable t) {
            throw new RDFHandlerException("unable to generate/write Turtle output", t);
        } finally {
        }
    }

    private void writeQName(IndentingWriter out, QName qname) throws Exception {
        if (qname == null) {
            out.write("null<QName>");
        } else if (qname.getPrefix() != null) {
            out.write(qname.getPrefix() + ":" + qname.getLocalPart());
        } else {
            out.write("<" + qname.getNamespaceURI() + qname.getLocalPart() + ">");
        }
    }

    private void writeSubjectTriples(IndentingWriter out, Resource subject) throws Exception {
        ArrayList<URI> firstPredicates = new ArrayList<URI>(); // predicates that are specially rendered first
        firstPredicates.add(rdfType);
        firstPredicates.add(rdfsSubClassOf);
        firstPredicates.add(rdfsSubPropertyOf);
        firstPredicates.add(owlSameAs);
        firstPredicates.add(rdfsLabel);
        firstPredicates.add(rdfsComment);

        TurtlePredicateObjectMap poMap = tripleMap.get(subject);
        QName qname = null; // try to write the subject out as a QName if possible.
        if (subject instanceof URI) {
            qname = convertUriToQName((URI)subject);
        }
        if (qname != null) {
            writeQName(out, qname);
        } else {
            out.write("<" + subject.stringValue() + ">");
        }
        out.writeEOL();
        out.increaseIndentation();

        // Write predicate/object pairs rendered first.
        for (URI predicate : firstPredicates) {
            if (poMap.containsKey(predicate)) {
                TurtleObjectList values = poMap.get(predicate);
                writePredicateAndObjectValues(out, predicate, values);
            }
        }

        // Write other predicate/object pairs.
        for (URI predicate : poMap.keySet()) {
            if (!firstPredicates.contains(predicate)) {
                TurtleObjectList values = poMap.get(predicate);
                writePredicateAndObjectValues(out, predicate, values);
            }
        }

        // Close statement
        out.write("."); out.writeEOL();

        out.decreaseIndentation();
        out.writeEOL(); // blank line
    }

    private void writePredicateAndObjectValues(IndentingWriter out, URI predicate, TurtleObjectList values) throws Exception {
        writePredicate(out, predicate);
        if (values.size() == 1) {
            writeObject(out, values.first());
            out.write(";");
            out.writeEOL();
        } else if (values.size() > 1) {
            out.writeEOL();
            out.increaseIndentation();
            for (Value value : values) {
                writeObject(out, value);
                out.write(",");
                out.writeEOL();
            }
            out.write(";");
            out.writeEOL();
            out.decreaseIndentation();
        }
    }

    private void writePredicate(IndentingWriter out, URI predicate) throws Exception {
        writeUri(out, predicate);
    }

    /**
     * Converts a URI to a QName, if possible, given the available namespace prefixes.  Returns null if there is no match to a prefix.
     * @param uri The URI to convert to a QName, if possible.
     * @return The equivalent QName for the URI, or null if no equivalent.
     */
    private QName convertUriToQName(URI uri) {
        // TODO: check that the local part (tail) of the QName only contains valid QName characters.
        String uriString = uri.stringValue();
        for (String uriStem : reverseNamespaceTable.keySet()) {
            if ((uriString.length() > uriStem.length()) && uriString.startsWith(uriStem)) {
                return new QName(uriStem, uriString.substring(uriStem.length()), reverseNamespaceTable.get(uriStem));
            }
        }
        // Failed to find a match, return null.
        return null;
    }

    private void writeUri(IndentingWriter out, URI uri) throws Exception {
        if(rdfType.equals(uri)) {
            out.write("a ");
        } else {
            QName qname = convertUriToQName(uri); // write the URI out as a QName if possible.
            if (qname != null) {
                writeQName(out, qname);
                out.write(" ");
            } else { // write out the URI relative to the base URI, if possible.
                String uriString = uri.stringValue();
                if (baseUri != null) {
                    String baseUriString = baseUri.stringValue();
                    if ((uriString.length() > baseUriString.length()) && uriString.startsWith(baseUriString)) {
                        out.write("#" + uriString.substring(baseUriString.length()) + " ");
                        return;
                    }
                }
                out.write("<" + uri.stringValue() + "> ");
            }
        }
    }

    private void writeObject(IndentingWriter out, Value value) throws Exception {
        // TODO: add other object types, handle quotes in strings, etc.
        if (value instanceof BNode) {
            writeObject(out, (BNode) value);
        } else if (value instanceof URI) {
            writeObject(out, (URI)value);
        } else if (value instanceof Literal) {
            writeObject(out, (Literal)value);
        } else {
            out.write("\"" + value.stringValue() + "\"");
            out.write(" ");
        }
    }

    private void writeObject(IndentingWriter out, BNode bnode) throws Exception {
        out.write("[] "); // TODO: fix blank nodes
    }

    private void writeObject(IndentingWriter out, URI uri) throws Exception {
        writeUri(out, uri);
    }

    private void writeObject(IndentingWriter out, Literal literal) throws Exception {
        if (literal == null) {
            out.write("null<Literal> ");
        } else if (literal.getLanguage() != null) {
            out.write("\"" + literal.stringValue() + "\"@" + literal.getLanguage() + " "); // TODO: handle character that need escaping
        } else if (literal.getDatatype() != null) {
            out.write("\"" + literal.stringValue() + "\"^^"); // TODO: handle character that need escaping
            writeUri(out, literal.getDatatype());
        } else {
            out.write("\"" + literal.stringValue() + "\""); // TODO: handle character that need escaping
        }
    }

    /**
     * Handles a statement.
     *
     * @param st The statement.
     * @throws org.openrdf.rio.RDFHandlerException If the RDF handler has encountered an unrecoverable error.
     */
    @Override
    public void handleStatement(Statement st) throws RDFHandlerException {
        // Store the statement in the main 'triple map'.
        TurtlePredicateObjectMap poMap = null;
        if (tripleMap.containsKey(st.getSubject())) {
            poMap = tripleMap.get(st.getSubject());
        } else {
            poMap = new TurtlePredicateObjectMap();
            tripleMap.put(st.getSubject(), poMap);
        }

        TurtleObjectList oList = null;
        if (poMap.containsKey(st.getPredicate())) {
            oList = poMap.get(st.getPredicate());
        } else {
            oList = new TurtleObjectList();
            poMap.put(st.getPredicate(), oList);
        }

        if (!oList.contains(st.getObject())) {
            oList.add(st.getObject());
            if (st.getObject() instanceof BNode) { // make a note of BNodes that are statement objects
                objectBlankNodes.add((BNode) st.getObject());
            }
        }

        // Note subjects which are OWL ontologies, as the are handled before other subjects.
        if (st.getPredicate().equals(rdfType) && st.getObject().equals(owlOntology)) {
            ontologies.add(st.getSubject());
        }
    }

    /**
     * Handles a comment.
     *
     * @param comment The comment.
     * @throws org.openrdf.rio.RDFHandlerException If the RDF handler has encountered an unrecoverable error.
     */
    @Override
    public void handleComment(String comment) throws RDFHandlerException {
        // NOTE: comments are suppressed, as it isn't clear how to sort them sensibly with triples.
    }
}
