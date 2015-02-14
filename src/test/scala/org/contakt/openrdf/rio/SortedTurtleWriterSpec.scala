package org.contakt.openrdf.rio

import org.openrdf.rio.turtle.{TurtleWriterFactory, TurtleWriter}

import scala.io.BufferedSource
import scala.language.postfixOps

import java.io._

import org.apache.log4j.{Level, Logger, BasicConfigurator}
import org.openrdf.model.impl.URIImpl
import org.openrdf.rio.{RDFFormat, Rio}
import org.scalatest.{Matchers, FlatSpec}

/**
 * ScalaTest tests for the SortedTurtleWriter and SortedTurtleWriterFactory.
 */
class SortedTurtleWriterSpec extends FlatSpec with Matchers {

  // Log4j setup
  BasicConfigurator configure ()
  Logger.getRootLogger setLevel Level.WARN

  // Set up directories for output files.
  def mkCleanDir(dirPath: String): File = {
    val newDir = new File(dirPath)
    if (newDir exists) { newDir delete () }
    newDir mkdirs ()
    newDir
  }
  val outputDir0 = mkCleanDir(s"target//temp//${classOf[TurtleWriter].getName}")
  val outputDir1 = mkCleanDir(s"target//temp//${this.getClass.getName}")
  val outputDir2 = mkCleanDir(s"target//temp//${this.getClass.getName}_2")

  /** Sets the extension part of a filename path, e.g. "ttl". */
  def setFilePathExtension(filePath: String, fileExtension: String): String = {
    if (filePath.contains(".")) {
      s"${filePath.substring(0, filePath.lastIndexOf("."))}.$fileExtension"
    } else {
      s"$filePath.$fileExtension"
    }
  }

  /** Reads the contents of a file into a String. */
  def getFileContents(file: File): String = new BufferedSource(new FileInputStream(file)).mkString

  "A SortedTurtleWriterFactory" should "be able to create a SortedTurtleWriter" in {
    val outWriter = new OutputStreamWriter(System.out)
    val factory = new SortedTurtleWriterFactory()

    val writer1 = new SortedTurtleWriter(System.out)
    assert(writer1 != null, "failed to create default SortedTurtleWriter from OutputStream")

    val writer2 = new SortedTurtleWriter(outWriter)
    assert(writer2 != null, "failed to create default SortedTurtleWriter from Writer")

    val writer3 = new SortedTurtleWriter(System.out, new URIImpl("http://example.com#"), "\t\t")
    assert(writer3 != null, "failed to create default SortedTurtleWriter from OutputStream wit parameters")

    val writer4 = new SortedTurtleWriter(outWriter, new URIImpl("http://example.com#"), "\t\t")
    assert(writer4 != null, "failed to create default SortedTurtleWriter from Writer")
  }

  "A TurtleWriter" should "be able to read various RDF documents and write them in sorted Turtle format" in {
    val rawTurtleDirectory = new File("src/test/resources/turtle/raw")
    assert(rawTurtleDirectory isDirectory, "raw turtle directory is not a directory")
    assert(rawTurtleDirectory exists, "raw turtle directory does not exist")

    var fileCount = 0
    for (sourceFile <- rawTurtleDirectory.listFiles()) {
      System.err.println("read/write: " + sourceFile.getName); // TODO: remove debugging
      fileCount += 1
      val targetFile = new File(outputDir0, setFilePathExtension(sourceFile getName, "ttl"))
      val outStream = new FileOutputStream(targetFile)
      val factory = new TurtleWriterFactory()
      val turtleWriter = factory getWriter (outStream)
      val rdfFormat = Rio getParserFormatForFileName(sourceFile getName, RDFFormat.TURTLE)
      val inputModel = Rio parse (new FileReader(sourceFile), "", rdfFormat)
      Rio write (inputModel, turtleWriter)
      outStream flush ()
      outStream close ()
    }
  }
  
  "A SortedTurtleWriter" should "be able to produce a sorted Turtle file" in {
    val inputFile = new File("src/test/resources/turtle/raw/topbraid-countries-ontology.ttl")
    val baseUri = new URIImpl("http://topbraid.org/countries")
    val outputFile = new File(outputDir1, setFilePathExtension(inputFile getName, "ttl"))
    val outStream = new FileOutputStream(outputFile)
    val factory = new SortedTurtleWriterFactory()
    val turtleWriter = factory getWriter (outStream, baseUri, null)
    val inputModel = Rio parse (new FileReader(inputFile), baseUri stringValue, RDFFormat.TURTLE)
    Rio write (inputModel, turtleWriter)
    outStream flush ()
    outStream close ()
  }

  it should "be able to produce a sorted Turtle file with blank object nodes" in {
    val inputFile = new File("src/test/resources/turtle/raw/topquadrant-extended-turtle-example.ttl")
    val outputFile = new File(outputDir1, setFilePathExtension(inputFile getName, "ttl"))
    val outStream = new FileOutputStream(outputFile)
    val factory = new SortedTurtleWriterFactory()
    val turtleWriter = factory getWriter (outStream)

    val inputModel = Rio parse (new FileReader(inputFile), "", RDFFormat.TURTLE)
    Rio write (inputModel, turtleWriter)
    outStream flush ()
    outStream close ()
  }

  it should "be able to produce a sorted Turtle file with blank subject nodes" in {
    val inputFile = new File("src/test/resources/turtle/raw/turtle-example-17.ttl")
    val outputFile = new File(outputDir1, setFilePathExtension(inputFile getName, "ttl"))
    val outStream = new FileOutputStream(outputFile)
    val factory = new SortedTurtleWriterFactory()
    val turtleWriter = factory getWriter (outStream)

    val inputModel = Rio parse (new FileReader(inputFile), "", RDFFormat.TURTLE)
    Rio write (inputModel, turtleWriter)
    outStream flush ()
    outStream close ()
  }

  it should "be able to read various RDF documents and write them in sorted Turtle format" in {
    val rawTurtleDirectory = new File("src/test/resources/turtle/raw")
    assert(rawTurtleDirectory isDirectory, "raw turtle directory is not a directory")
    assert(rawTurtleDirectory exists, "raw turtle directory does not exist")

    var fileCount = 0
    for (sourceFile <- rawTurtleDirectory.listFiles()) {
      System.err.println("read/write #0: " + sourceFile.getName); // TODO: remove debugging
      fileCount += 1
      val targetFile = new File(outputDir1, setFilePathExtension(sourceFile getName, "ttl"))
      RDFFormatter run Array[String](
        "-s", sourceFile getAbsolutePath,
        "-t", targetFile getAbsolutePath
      )
    }
  }

  it should "be able to sort RDF triples consistently when writing in Turtle format" in {
    val rawTurtleDirectory = new File("src/test/resources/turtle/raw")
    assert(rawTurtleDirectory isDirectory, "raw turtle directory is not a directory")
    assert(rawTurtleDirectory exists, "raw turtle directory does not exist")

    // Serialise sample files as sorted Turtle.
    var fileCount = 0
    for (sourceFile <- rawTurtleDirectory.listFiles()) {
      System.err.println("read/write #1: " + sourceFile.getName); // TODO: remove debugging
      fileCount += 1
      val targetFile = new File(outputDir1, setFilePathExtension(sourceFile getName, "ttl"))
      RDFFormatter run Array[String](
        "-s", sourceFile getAbsolutePath,
        "-t", targetFile getAbsolutePath
      )
    }

    // Re-serialise the sorted files, again as sorted Turtle.
    fileCount = 0
    for (sourceFile <- outputDir1.listFiles()) {
      System.err.println("read/write #2: " + sourceFile.getName); // TODO: remove debugging
      fileCount += 1
      val targetFile = new File(outputDir2, setFilePathExtension(sourceFile getName, "ttl"))
      RDFFormatter run Array[String](
        "-s", sourceFile getAbsolutePath,
        "-t", targetFile getAbsolutePath
      )
    }

    // Check that re-serialising the Turtle files has changed nothing.
    fileCount = 0
    for (file2 <- outputDir2.listFiles()) {
      System.err.println("read/write #3: " + file2.getName); // TODO: remove debugging
      fileCount += 1
      val file = new File(outputDir1, file2 getName)
      val contents1 = getFileContents(file)
      val contents2 = getFileContents(file2)
      assert(contents1 === contents2, s"match failed for file: ${file getAbsolutePath}")
    }
  }

}
