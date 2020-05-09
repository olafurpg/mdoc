package mdoc.internal.io

import fansi.Attrs
import fansi.Color._
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicBoolean
import scala.meta.Position
import mdoc.Reporter
import mdoc.internal.pos.PositionSyntax._
import coursierapi.Logger

class ConsoleReporter(
    ps: PrintStream,
    green: Attrs = Green,
    blue: Attrs = Blue,
    yellow: Attrs = Yellow,
    red: Attrs = Red
) extends Reporter {

  def formatMessage(pos: Position, severity: String, message: String): String =
    pos.formatMessage("", message)

  private val myDebug = green("debug")
  private val myInfo = blue("info")
  private val myWarning = yellow("warning")
  private val myError = red("error")

  private var myWarnings = 0
  private var myErrors = 0

  private val isDebugEnabled = new AtomicBoolean(false)

  override def warningCount: Int = myWarnings
  override def errorCount: Int = myErrors
  def hasWarnings: Boolean = myWarnings > 0
  def hasErrors: Boolean = myErrors > 0
  def reset(): Unit = {
    myWarnings = 0
    myErrors = 0
  }

  def error(throwable: Throwable): Unit = {
    error(Position.None, throwable)
  }

  def error(pos: Position, throwable: Throwable): Unit = {
    error(pos, throwable.message)
    throwable.printStackTrace(ps)
  }
  def error(pos: Position, msg: String): Unit = {
    error(formatMessage(pos.toUnslicedPosition, "error", msg))
  }
  def error(msg: String): Unit = {
    myErrors += 1
    ps.println(myError ++ s": $msg")
  }
  def warning(pos: Position, msg: String): Unit = {
    warning(formatMessage(pos.toUnslicedPosition, "warning", msg))
  }
  def warning(msg: String): Unit = {
    myWarnings += 1
    ps.println(myWarning ++ s": $msg")
  }
  def info(pos: Position, msg: String): Unit = {
    info(formatMessage(pos.toUnslicedPosition, "info", msg))
  }
  def info(msg: String): Unit = {
    ps.println(myInfo ++ s": $msg")
  }
  def debug(msg: => String): Unit = {
    if (isDebugEnabled.get()) {
      ps.println(myDebug ++ s": $msg")
    }
  }

  override def setDebugEnabled(isDebugEnabled: Boolean): Unit = {
    this.isDebugEnabled.set(isDebugEnabled)
  }

  override def print(msg: String): Unit = {
    ps.print(msg)
  }
  override def println(msg: String): Unit = {
    ps.println(msg)
  }
}

object ConsoleReporter {
  def default: Reporter = new ConsoleReporter(System.out)
}
