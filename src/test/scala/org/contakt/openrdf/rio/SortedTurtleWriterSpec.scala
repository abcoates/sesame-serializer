package org.contakt.openrdf.rio

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
  val outputDir = mkCleanDir(s"target//temp//${this.getClass.getName}")
  
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
  
  "A SortedTurtleWriter" should "be able to produce a sorted Turtle file" in {
    val inputFile = new File("src/test/resources/turtle/raw/topbraid-countries-ontology.ttl")
    val baseUri = new URIImpl("http://topbraid.org/countries")
    val outputFile = new File(outputDir, inputFile getName)
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
    val outputFile = new File(outputDir, inputFile getName)
    val outStream = new FileOutputStream(outputFile)
    val factory = new SortedTurtleWriterFactory()
    val turtleWriter = factory getWriter (outStream)

    val inputModel = Rio parse (new FileReader(inputFile), "", RDFFormat.TURTLE)
    Rio write (inputModel, turtleWriter)
    outStream flush ()
    outStream close ()
  }

}
