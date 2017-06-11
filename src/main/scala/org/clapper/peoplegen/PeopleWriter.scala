package org.clapper.peoplegen

import java.io.{FileWriter, OutputStreamWriter, Writer}
import java.nio.file.Path
import java.text.SimpleDateFormat

import scala.util.Try

/** The `PeopleWriter` class must be passed an instance of this class,
  * which controls how the output is opened. This strategy makes it easier
  * to test the writer.
  */
trait OutputHandler {

  /** Opens the output file.
    *
    * @param outputFile The output file to open. If `None`, standard output
    *                   should be used. Testers can override this behavior.
    * @param code       The code to run with the open output stream.
    *
    * @tparam T         The type returned within the `Try` from the code block.
    *
    * @return whatever the code block returns.
    *
    */
  def withOutputFile[T](outputFile: Option[Path])(code: Writer => Try[T]): Try[T]
}

/** Writes a stream of people in the appropriate format. Must be coupled
  * with an OutputHandler. For production use, use the `MainPropleWriter`.
  */
trait PeopleWriter {

  self: OutputHandler =>

  /** the parsed command line parameters
    */
  val params: Params

  /** the message handler to use to emit messages
    */
  val msg: MessageHandler

  private val BirthDateFormat        = new SimpleDateFormat("yyyy-MM-dd")
  private val VerbosePersonThreshold = 1000

  private object Headers extends Enumeration {
    type Headers = Value

    val FirstName  = Value
    val MiddleName = Value
    val LastName   = Value
    val BirthDate  = Value
    val SSN        = Value
    val Gender     = Value
    val Salary     = Value

    def inOrder = Seq(
      FirstName, MiddleName, LastName, Gender, BirthDate, SSN, Salary
    )
  }

  private val HeaderNames = Map(
    HeaderFormat.English -> Map(
      Headers.FirstName  -> "first name",
      Headers.MiddleName -> "middle name",
      Headers.LastName   -> "last name",
      Headers.BirthDate  -> "birth date",
      Headers.SSN        -> "ssn",
      Headers.Gender     -> "gender",
      Headers.Salary     -> "salary"
    ),
    HeaderFormat.CamelCase -> Map(
      Headers.FirstName  -> "firstName",
      Headers.MiddleName -> "middleName",
      Headers.LastName   -> "lastName",
      Headers.BirthDate  -> "birthDate",
      Headers.SSN        -> "ssn",
      Headers.Gender     -> "gender",
      Headers.Salary     -> "salary"
    ),
    HeaderFormat.SnakeCase -> Map(
      Headers.FirstName  -> "first_name",
      Headers.MiddleName -> "middle_name",
      Headers.LastName   -> "last_name",
      Headers.BirthDate  -> "birth_date",
      Headers.SSN        -> "ssn",
      Headers.Gender     -> "gender",
      Headers.Salary     -> "salary"
    )
  )

  def write(people: Stream[Person]): Try[Unit] = {
    msg.verbose(s"Writing ${params.totalPeople} people records.")

    withOutputFile(params.outputFile) { w =>
      params.fileFormat match {
        case FileFormat.CSV  => writeCSV(people, w)
        case FileFormat.JSON => writeJSON(people, w)
      }
    }
  }

  private def atVerboseThreshold(index: Int): Boolean = {
    ((index + 1) % VerbosePersonThreshold) == 0
  }

  private def writeCSV(people: Stream[Person], out: Writer): Try[Unit] = {
    import com.github.tototoshi.csv.{CSVWriter, DefaultCSVFormat}

    def getHeaders: List[String] = {
      Headers.inOrder.map { h => HeaderNames(params.headerFormat)(h) }.toList
    }

    def personToCSVFields(p: Person): List[String] = {
      Headers.inOrder.flatMap {
        case Headers.SSN =>
          if (params.generateSSNs) Some(p.ssn) else None
        case Headers.Salary =>
          if (params.generateSalaries) Some(p.salary.toString) else None
        case Headers.BirthDate =>
          Some(BirthDateFormat.format(p.birthDate))
        case Headers.FirstName =>
          Some(p.firstName)
        case Headers.MiddleName =>
          Some(p.middleName)
        case Headers.LastName =>
          Some(p.lastName.toString)
        case Headers.Gender =>
          Some(p.gender.toString)
      }
      .toList
    }

    Try {
      implicit object MyFormat extends DefaultCSVFormat {
        override val delimiter = params.columnSep
      }

      val w = CSVWriter.open(out)
      if (params.generateHeader)
        w.writeRow(getHeaders)

      for ((p, i) <- people.zipWithIndex) {
        if (atVerboseThreshold(i)) msg.verbose(s"... ${i + 1}")
        w.writeRow(personToCSVFields(p))
      }
    }
  }

  private def writeJSON(people: Stream[Person], out: Writer): Try[Unit] = {
    import spray.json._

    object PersonProtocol extends DefaultJsonProtocol {
      implicit object PersonJsonFormat extends RootJsonFormat[Person] {
        def write(p: Person): JsValue = {
          val names = HeaderNames(params.headerFormat)
          val fields = Headers.inOrder.flatMap {
            case h @ Headers.SSN =>
              if (params.generateSSNs)
                Some(names(h) -> JsString(p.ssn))
              else
                None
            case h @ Headers.Salary =>
              if (params.generateSalaries)
                Some(names(h) -> JsNumber(p.salary))
              else
                None
            case h @ Headers.Gender =>
              Some(names(h) -> JsString(p.gender.toString))
            case h @ Headers.FirstName =>
              Some(names(h) -> JsString(p.firstName))
            case h @ Headers.MiddleName =>
              Some(names(h) -> JsString(p.middleName))
            case h @ Headers.LastName =>
              Some(names(h) -> JsString(p.lastName))
            case h @ Headers.BirthDate =>
              Some(names(h) -> JsString(BirthDateFormat.format(p.birthDate)))
          }

          JsObject(fields: _*)
        }

        def read(value: JsValue): Person = {
          deserializationError("Read not supported")
        }
      }
    }

    import PersonProtocol._

    def convertToJSONArray = {
      msg.verbose(s"Converting ${params.totalPeople} people records to JSON.")
      people.toSeq.toJson
    }

    Try {

      if (params.prettyJSON) {
        val jsonString = convertToJSONArray.prettyPrint
        msg.verbose(s"Writing pretty-printed JSON array.")
        out.write(s"$jsonString\n")
      }
      else if (params.jsonFormat == JSONFormat.AsArray) {
        val jsonString = convertToJSONArray.compactPrint
        msg.verbose(s"Writing compact JSON array.")
        out.write(s"$jsonString\n")
      }

      else {
        for ((p, i) <- people.zipWithIndex) {
          if (atVerboseThreshold(i)) msg.verbose(s"... ${i + 1}")
          val jsonString = p.toJson.compactPrint
          out.write(s"$jsonString\n")
        }
      }
    }
  }
}

/** The "production" output handler.
  *
  * @param params the parsed command line parameters
  * @param msg    the message handler to use to emit messages
  */
class MainPeopleWriter(val params: Params, val msg: MessageHandler)
  extends PeopleWriter with OutputHandler {

  /** Handles writing the output to the actual destinations.
    *
    * @param outputFile The output file to open. If `None`, standard output
    *                   should be used. Testers can override this behavior.
    * @param code       The code to run with the open output stream.
    *
    * @tparam T         The type returned within the `Try` from the code block.
    *
    * @return whatever the code block returns.
    */
  def withOutputFile[T](outputFile: Option[Path])
                       (code: Writer => Try[T]): Try[T] = {
    outputFile.map { path =>
      import grizzled.util.withResource
      import grizzled.util.CanReleaseResource.Implicits.CanReleaseCloseable

      withResource(new FileWriter(path.toFile)) { w =>
        code(w)
      }
    }
    .getOrElse {
      for { f   <- Try { new OutputStreamWriter(System.out) }
            res <- code(f)
            _   <- Try { f.flush() } }
        yield res
    }
  }
}
