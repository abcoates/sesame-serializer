package org.contakt.data.semweb.sesame

import scala.language.{implicitConversions, postfixOps}

import org.apache.log4j.{ConsoleAppender, Level, Logger, BasicConfigurator}

import java.io.{FileWriter, FileReader, File}

import org.openrdf.model.Model
import org.openrdf.repository.Repository
import org.openrdf.repository.sail.SailRepository
import org.openrdf.rio.{RDFFormat, Rio}
import org.openrdf.sail.memory.MemoryStore
import org.scalatest.{Matchers, FlatSpec}

/**
 * ScalaTest tests for Sesame Rio I/O functionality, to provide a baseline and sanity checking.
 */
class SesameRioSpec extends FlatSpec with Matchers {

  // Log4j setup
  BasicConfigurator configure ()
  Logger.getRootLogger setLevel Level.WARN

  // Set up directory for output files.
  val outputDir = new File(s"target//temp//${this.getClass.getName}")
  if (outputDir exists) { outputDir delete () }
  outputDir mkdirs ()

  /**
   * Creates an in-memory Sesame RDF repository with no inferencing.
   * @return a new Sesame repository
   */
  def newSesameRepository: Repository = {
    val repository = new SailRepository(new MemoryStore())
    repository initialize ()
    repository
  }

  /**
   * Reads a Turtle RDF file into a Sesame Model.
   * @param turtleFile input Turtle file
   * @return model containing ingested RDF statements
   */
  def readTurtle(turtleFile: File): Model = {
    Rio parse (new FileReader(turtleFile), "", RDFFormat.TURTLE)
  }

  /**
   * Writes a Turtle RDF file for a Sesame Model.
   * @param model model containing RDF statements
   * @param turtleFile output Turtle file
   * @return model containing ingested RDF statements
   */
  def writeTurtle(model: Model, turtleFile: File): Unit = {
    val out = new FileWriter(turtleFile)
    Rio write (model, out, RDFFormat.TURTLE)
    out flush ()
  }

  /** Converts a file path string into a File object. */
  implicit def stringToFile(filePath: String): File = new File(filePath)

  "A Sesame RDF repository" should "be able to read and write RDF documents in Turtle format" in {
    val rawTurtleDirectory = new File("src/test/resources/turtle/raw")
    assert(rawTurtleDirectory isDirectory, "raw turtle directory is not a directory")
    assert(rawTurtleDirectory exists, "raw turtle directory does not exist")

    print("Reading/writing RDF: ")
    var fileCount = 0
    for (file <- rawTurtleDirectory.listFiles()) {
      fileCount += 1
      print(s"($fileCount=${file.getName}) r ")
      val model = readTurtle(file)
      print("w ")
      writeTurtle(model, new File(outputDir, file getName))
    }
    println()
  }

}
