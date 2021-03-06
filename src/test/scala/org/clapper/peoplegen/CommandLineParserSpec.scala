package org.clapper.peoplegen
import java.util.Date

class CommandLineParserSpec extends BaseSpec {

  object TestCommandLineParser extends CommandLineParserBase {
    val stdoutBuffer = new MessageBuffer
    val stderrBuffer = new MessageBuffer

    protected val errorStream  = stderrBuffer.asPrintStream
    protected val outputStream = stdoutBuffer.asPrintStream
  }

  "parseParams" should "fail for an incorrect option" in {
    val args = Array("-x")

    TestCommandLineParser.parseParams(args) shouldBe Symbol("failure")
    TestCommandLineParser.stderrBuffer.asString should include ("Usage")
  }

  it should "succeed with valid parameters" in {
    val args = Array("-F", "json", "--female", "10", "100")
    TestCommandLineParser.parseParams(args) shouldBe Symbol("success")
  }

  it should "fail if male and female percentages are less than 100" in {
    val args = Array("--female", "10", "--male", "10", "1000")
    TestCommandLineParser.parseParams(args) shouldBe Symbol("failure")
  }

  it should "fail if male and female percentages are more than 100" in {
    val args = Array("--female", "10", "--male", "100", "1000")
    TestCommandLineParser.parseParams(args) shouldBe Symbol("failure")
  }

  it should "succeed if male and female percentages are exactly 100" in {
    val args = Array("--male", "10", "--female", "90", "1000")
    TestCommandLineParser.parseParams(args) shouldBe Symbol("success")
  }

  it should "succeed if only --male is specified" in {
    val args = Array("--male", "10", "100")
    val t = TestCommandLineParser.parseParams(args)
    t shouldBe Symbol("success")
    val params = t.get
    params.malePercent shouldBe 10
    params.femalePercent shouldBe 90
  }

  it should "succeed if only --female is specified" in {
    val args = Array("--female", "80", "100")
    val t = TestCommandLineParser.parseParams(args)
    t shouldBe Symbol("success")
    val params = t.get
    params.malePercent shouldBe 20
    params.femalePercent shouldBe 80
  }

  it should "fail if --year-min exceeds --year-max" in {
    val args = Array("--year-min", "1965", "--year-max", "1925")
    val t = TestCommandLineParser.parseParams(args)
    t shouldBe Symbol("failure")
  }

  it should "succeed if --year-min doesn't exceed --year-max" in {
    val args = Array("--year-min", "1920", "--year-max", "1990", "1000")
    val t = TestCommandLineParser.parseParams(args)
    t shouldBe Symbol("success")
  }
}
