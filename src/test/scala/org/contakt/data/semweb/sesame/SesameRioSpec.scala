package org.contakt.data.semweb.sesame

import org.openrdf.model.impl.{ContextStatementImpl, BNodeImpl, LinkedHashModel}

import scala.collection.JavaConversions._
import scala.collection.immutable.List
import scala.io.BufferedSource
import scala.language.{implicitConversions, postfixOps}

import org.apache.log4j.{ConsoleAppender, Level, Logger, BasicConfigurator}

import java.io.{FileInputStream, FileWriter, FileReader, File}

import org.openrdf.model.{BNode, Statement, Model}
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

  // Set up directories for output files.
  def mkCleanDir(dirPath: String): File = {
    val newDir = new File(dirPath)
    if (newDir exists) { newDir delete () }
    newDir mkdirs ()
    newDir
  }
  val outputDir = mkCleanDir(s"target//temp//${this.getClass.getName}")
  val outputDir2 = mkCleanDir(s"target//temp//${this.getClass.getName}_2")

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

  /**
   * Formats the blank nodes in a Statement to remove Sesame's 'genid' labelling in blank node IDs.
   * @param stmt a Sesame statement (triple or quad)
   * @return new Statement with reformatted blank node IDs
   */
  def formatBlankNodes(stmt: Statement): Statement = {
    val newSubject = stmt.getSubject match {
      case bnode: BNode => new BNodeImpl(bnode.getID.replaceAll("genid-[0-9a-f]+-", ""))
      case other => other
    }
    val newObject = stmt.getObject match {
      case bnode: BNode => new BNodeImpl(bnode.getID.replaceAll("genid-[0-9a-f]+-", ""))
      case other => other
    }
    val newContext = stmt.getContext match {
      case bnode: BNode => new BNodeImpl(bnode.getID.replaceAll("genid-[0-9a-f]+-", ""))
      case other => other
    }
    new ContextStatementImpl(newSubject, stmt getPredicate, newObject, newContext)
  }

  /**
   * Sorts the triples in a model, keeping the namespace mappings intact.
   * @param model model to be sorted
   * @return new model with sorted triples and original namespace mappings
   */
  def sortModel(model: Model): Model = {
    val unsortedTriples = List[Statement]() ++ model.iterator()
    val sortedTriples = unsortedTriples.map(formatBlankNodes).sortWith(ltStatement)
//    for (stmt <- sortedTriples) {
//      stmt match {
//        case bnode: BNode => print(s"[b]${bnode.getID} ")
//        case genid: Statement if genid.getSubject.stringValue.contains("genid-") =>
//          print(s"[g]${genid.getSubject.stringValue} ")
//          val bnodeId = genid.getSubject.stringValue.replaceAll("genid-[0-9a-f]+-", "")
//          val bgen = new BNodeImpl(bnodeId)
//          print(s"[b]${bgen.getID} [[${bgen.toString}]] ")
//        case _ => // ignore
//      }
//    }
    new LinkedHashModel(model getNamespaces, sortedTriples)
  }

  /**
   * Compares two Sesame statements to test if x comes before y in order (x < y).
   * @param x left-hand Statement
   * @param y right-hand Statement
   * @return true if x < y, false otherwise
   */
  def ltStatement(x: Statement, y:Statement): Boolean = {
    val sCompare = x.getSubject.stringValue.compareTo(y.getSubject.stringValue)
    if (sCompare < 0) {
      true
    } else if (sCompare == 0) {
      val pCompare = x.getPredicate.stringValue.compareTo(y.getPredicate.stringValue)
      if (pCompare < 0) {
        true
      } else if (pCompare == 0) {
        val oCompare = x.getObject.stringValue.compareTo(y.getObject.stringValue)
        oCompare < 0
      } else {
        false
      }
    } else {
      false
    }
  }

  /** Converts a file path string into a File object. */
  implicit def stringToFile(filePath: String): File = new File(filePath)

  /** Reads the contents of a file into a String. */
  def getFileContents(file: File): String = new BufferedSource(new FileInputStream(file)).mkString

  /** File prefix for sorted RDF files. */
  val sortedFilePrefix = "sorted."

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

  it should "be able to read and write sorted RDF documents in Turtle format" in {
    val rawTurtleDirectory = new File("src/test/resources/turtle/raw")
    assert(rawTurtleDirectory isDirectory, "raw turtle directory is not a directory")
    assert(rawTurtleDirectory exists, "raw turtle directory does not exist")

    print("Reading/writing sorted RDF: ")
    var fileCount = 0
    for (file <- rawTurtleDirectory.listFiles()) {
      fileCount += 1
      print(s"($fileCount=${file.getName}) rw ")
      writeTurtle(sortModel(readTurtle(file)), new File(outputDir, s"$sortedFilePrefix${file getName}"))
    }
    println()
  }

  it should "be able to sort RDF triples consistently when writing in Turtle format" in {
    val rawTurtleDirectory = new File("src/test/resources/turtle/raw")
    assert(rawTurtleDirectory isDirectory, "raw turtle directory is not a directory")
    assert(rawTurtleDirectory exists, "raw turtle directory does not exist")

    print("Reading/writing sorted RDF (1): ")
    var fileCount = 0
    for (file <- rawTurtleDirectory.listFiles()) {
      fileCount += 1
      print(s"($fileCount=${file.getName}) rw ")
      writeTurtle(sortModel(readTurtle(file)), new File(outputDir, s"$sortedFilePrefix${file getName}"))
    }
    println()

    print("Reading/writing sorted RDF (2): ")
    fileCount = 0
    for (file <- outputDir.listFiles() if file.getName.startsWith(sortedFilePrefix)) {
      fileCount += 1
      print(s"($fileCount=${file.getName}) rw ")
      writeTurtle(sortModel(readTurtle(file)), new File(outputDir2, file getName))
    }
    println()

    print("Comparing sorted RDF: ")
    fileCount = 0
    for (file2 <- outputDir2.listFiles()) {
      fileCount += 1
      print(s"($fileCount=${file2.getName}) ")
      val file = new File(outputDir, file2 getName)
      val contents1 = getFileContents(file)
      val contents2 = getFileContents(file2)
      assert(contents1 === contents2, s"match failed for file: ${file getAbsolutePath}")
      print("OK ")
    }
    println("Done.")
  }

}
