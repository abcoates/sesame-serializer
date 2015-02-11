package org.contakt.openrdf.rio;

import org.apache.commons.cli.*;
import org.openrdf.model.Model;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.Rio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * RDF formatter that formats in a consistent order, friendly for version control systems.
 * Should not be used with files that are too large to be fully loaded into memory for sorting.
 * Run with "--help" option for help.
 */
public class RDFFormatter {

    private static final String SOURCE_FORMATS =
            "auto (select by filename) [default], " +
            "binary, " +
            "json-ld (JSON-LD), " +
            "n3, " +
            "n-quads (N-quads), " +
            "n-triples (N-triples), " +
            "rdf-a (RDF/A), " +
            "rdf-json (RDF/JSON), " +
            "rdf-xml (RDF/XML), " +
            "trig (TriG), " +
            "trix (TriX), " +
            "turtle (Turtle)";

    private static final String TARGET_FORMATS =
            "turtle (Turtle, sorted) [default]";

    private static Options options = null;

    static {
        // Create list of program options, using Apache Commons CLI library.
        options = new Options();
        options.addOption(
                "s", "source", true, "source (input) RDF file to be formatting"
        );
        options.addOption(
                "sfmt", "source-format", true, "source (input) RDF format; one of: " + SOURCE_FORMATS
        );
        options.addOption(
                "t", "target", true, "target (output) RDF file"
        );
        options.addOption(
                "tfmt", "target-format", true, "source (input) RDF format: one of: " + TARGET_FORMATS
        );
        options.addOption(
                "h", "help", false, "print out details of the command-line arguments for the program"
        );
    }

    /** Main method for running the RDF formatter. Run with "--help" option for help. */
    public static void main(String[] args) {
        // Main program block.
        try {
            run(args);
        } catch (Throwable t) {
            System.err.println(RDFFormatter.class.getSimpleName() + ": stopped by unexpected exception:");
            System.err.println(t.getClass().getSimpleName() + ": " + t.getMessage());
            System.err.println(t.getStackTrace());
            System.err.println();
            usage(options);
        }

    }

    /** Main method, but throws exceptions for use from inside other Java code. */
    public static void run(String[] args) throws Exception {
        final String baseUri = "";

        final String indent = "\t\t";

        // Parse the command line options.
        CommandLineParser parser = new BasicParser();
        CommandLine line = parser.parse( options, args );

        // Print out help, if requested.
        if (line.hasOption("h")) {
            usage(options);
            return;
        }

        // Check if required arguments provided.
        if (!line.hasOption("s")) {
            System.err.println("No source (input) file specified, nothing to format.  Use --help for help.");
            return;
        }
        if (!line.hasOption("t")) {
            System.err.println("No target (target) file specified, cannot format source.  Use --help for help.");
            return;
        }

        // Check if source files exists.
        String sourceFilePath = line.getOptionValue("s");
        File sourceFile = new File(sourceFilePath);
        if (!sourceFile.exists()) {
            System.err.println("Source file does not exist: " + sourceFilePath);
            return;
        }
        if (!sourceFile.isFile()) {
            System.err.println("Source file is not a file: " + sourceFilePath);
            return;
        }
        if (!sourceFile.canRead()) {
            System.err.println("Source file is not readable: " + sourceFilePath);
            return;
        }

        // Check if target file can be written.
        String targetFilePath = line.getOptionValue("t");
        File targetFile = new File(targetFilePath);
        if (targetFile.exists()) {
            if (!targetFile.isFile()) {
                System.err.println("Target file is not a file: " + targetFilePath);
                return;
            }
            if (!targetFile.canWrite()) {
                System.err.println("Target file is not writable: " + targetFilePath);
                return;
            }
        }

        // Create directory for target file, if required.
        File targetFileDir = targetFile.getParentFile();
        targetFileDir.mkdirs();
        if (!targetFileDir.exists()) {
            System.err.println("Target file directory could not be created: " + targetFileDir.getAbsolutePath());
            return;
        }

        // Load RDF file.
        String sourceFormat = "auto";
        if (line.hasOption("sfmt")) {
            sourceFormat = line.getOptionValue("sfmt");
        }
        RDFFormat sesameSourceFormat = null;
        if ("auto".equals(sourceFormat)) {
            sesameSourceFormat = Rio.getParserFormatForFileName(sourceFilePath, RDFFormat.TURTLE);
        } else {
            sesameSourceFormat = parseFormat(sourceFormat);
        }
        if (sesameSourceFormat == null) {
            System.err.println("Unsupported or unrecognised source format: " + sourceFormat);
        }
        Model sourceModel = Rio.parse(new FileInputStream(sourceFile), baseUri, sesameSourceFormat);

        // Write sorted RDF file.
        if (line.hasOption("tfmt")) {
            // Check target format, but currently only "turtle" is supported.
            String targetFormat = line.getOptionValue("tfmt");
            if (!"turtle".equals(targetFormat)) {
                System.err.println("Unsupported or unrecognised target format: " + targetFormat);
            }
        }
        OutputStream targetStream = new FileOutputStream(targetFile);
        SortedTurtleWriterFactory factory = new SortedTurtleWriterFactory();
        RDFWriter turtleWriter = factory.getWriter(targetStream, /*baseUri*/ null, indent);
        Rio.write(sourceModel, turtleWriter);
        targetStream.flush();
        targetStream.close();
    }

    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "RDFFormatter", options );
    }

    /** Converts a command-line RDF type name into the appropriate Sesame type value. */
    private static RDFFormat parseFormat(String format) {
        switch(format) {
            case "binary": return RDFFormat.BINARY;
            case "json-ld": return RDFFormat.JSONLD;
            case "n3": return RDFFormat.N3;
            case "n-quads": return  RDFFormat.NQUADS;
            case "n-triples": return RDFFormat.NTRIPLES;
            case "rdf-a": return RDFFormat.RDFA;
            case "rdf-json": return RDFFormat.RDFJSON;
            case "rdf-xml": return RDFFormat.RDFXML;
            case "trig": return RDFFormat.TRIG;
            case "trix": return RDFFormat.TRIX;
            case "turtle": return RDFFormat.TURTLE;
        }
        return null;
    }

}
