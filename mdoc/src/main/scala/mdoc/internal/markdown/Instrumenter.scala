package mdoc.internal.markdown

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import scala.meta._
import scala.meta.inputs.Position
import Instrumenter.position
import mdoc.internal.markdown.Instrumenter.Binders
import scala.meta.Mod.Lazy
import scala.collection.mutable
import mdoc.Reporter

class Instrumenter(sections: List[SectionInput]) {
  def instrument(reporter: Reporter): Instrumented = {
    printAsScript()
    Instrumented.fromSource(
      out.toString,
      scalacOptions.toList,
      dependencies.toList,
      repositories.toList,
      reporter
    )
  }
  private val scalacOptions = mutable.ListBuffer.empty[Name.Indeterminate]
  private val dependencies = mutable.ListBuffer.empty[Name.Indeterminate]
  private val repositories = mutable.ListBuffer.empty[Name.Indeterminate]
  private val out = new ByteArrayOutputStream()
  private val sb = new PrintStream(out)
  val gensym = new Gensym()
  val nest = new Nesting(sb)
  private def printAsScript(): Unit = {
    sections.zipWithIndex.foreach {
      case (section, i) =>
        if (section.mod.isReset) {
          nest.unnest()
          sb.print(Instrumenter.reset(section.mod, gensym.fresh("App")))
        } else if (section.mod.isNest) {
          nest.nest()
        }
        sb.println("\n$doc.startSection();")
        if (section.mod.isFailOrWarn) {
          sb.println(s"$$doc.startStatement(${position(section.source.pos)});")
          val out = new FailInstrumenter(sections, i).instrument()
          val literal = Instrumenter.stringLiteral(out)
          val binder = gensym.fresh("res")
          sb.append("val ")
            .append(binder)
            .append(" = _root_.mdoc.internal.document.FailSection(")
            .append(literal)
            .append(", ")
            .append(position(section.source.pos))
            .append(");")
          printBinder(binder, section.source.pos)
          sb.println("\n$doc.endStatement();")
        } else if (section.mod.isCompileOnly) {
          section.source.stats.foreach { stat =>
            sb.println(s"$$doc.startStatement(${position(stat.pos)});")
            sb.println("\n$doc.endStatement();")
          }
          sb.println(s"""object ${gensym.fresh("compile")} {""")
          sb.println(section.source.pos.text)
          sb.println("\n}")
        } else {
          section.source.stats.foreach { stat =>
            sb.println(s"$$doc.startStatement(${position(stat.pos)});")
            printStatement(stat, section.mod, sb)
            sb.println("\n$doc.endStatement();")
          }
        }
        sb.println("$doc.endSection();")
    }
    nest.unnest()
  }

  private def printBinder(name: String, pos: Position): Unit = {
    sb.print(s"; $$doc.binder($name, ${position(pos)})")
  }
  private def printStatement(stat: Stat, m: Modifier, sb: PrintStream): Unit = {
    if (m.isCrash) {
      sb.append("$doc.crash(")
        .append(position(stat.pos))
        .append(") {\n")
        .append(stat.pos.text)
        .append("\n}")
    } else {
      val binders = stat match {
        case Binders(names) =>
          names.map(name => name -> name.pos)
        case _ =>
          val fresh = gensym.fresh("res")
          sb.print(s"val $fresh = ")
          List(Name(fresh) -> stat.pos)
      }
      stat match {
        case i: Import =>
          i.importers.foreach {
            case Importer(
                Term.Name("$ivy" | "$dep"),
                List(Importee.Name(dep: Name.Indeterminate))
                ) =>
              dependencies += dep
            case Importer(
                Term.Name("$repo"),
                List(Importee.Name(repo: Name.Indeterminate))
                ) =>
              repositories += repo
            case Importer(
                Term.Name("$scalac"),
                List(Importee.Name(option: Name.Indeterminate))
                ) =>
              scalacOptions += option
            case importer =>
              sb.print("import ")
              sb.print(importer.syntax)
              sb.print(";")
          }
        case _ =>
          sb.print(stat.pos.text)
      }
      binders.foreach {
        case (name, pos) =>
          printBinder(name.syntax, pos)
      }
    }
  }
}
object Instrumenter {
  def reset(mod: Modifier, identifier: String): String = {
    val ctor =
      if (mod.isResetClass) s"new $identifier()"
      else identifier
    val keyword =
      if (mod.isResetClass) "class"
      else "object"
    s"$ctor\n}\n$keyword $identifier {\n"
  }
  def instrument(sections: List[SectionInput], reporter: Reporter): Instrumented = {
    val instrumented = new Instrumenter(sections).instrument(reporter)
    instrumented.copy(source = wrapBody(instrumented.source))
  }

  def position(pos: Position): String = {
    s"${pos.startLine}, ${pos.startColumn}, ${pos.endLine}, ${pos.endColumn}"
  }

  def stringLiteral(string: String): String = {
    import scala.meta.internal.prettyprinters._
    enquote(string, DoubleQuotes)
  }

  def wrapBody(body: String): String = {
    val wrapped = new StringBuilder()
      .append("package repl\n")
      .append("object Session extends _root_.mdoc.internal.document.DocumentBuilder {\n")
      .append("  def app(): _root_.scala.Unit = {val _ = new App()}\n")
      .append("  class App {\n")
      .append(body)
      .append("  }\n")
      .append("}\n")
      .toString()
    wrapped
  }
  object Binders {
    def binders(pat: Pat): List[Name] =
      pat.collect { case m: Member => m.name }
    def unapply(tree: Tree): Option[List[Name]] = tree match {
      case Defn.Val(mods, _, _, _) if mods.exists(_.isInstanceOf[Lazy]) => Some(Nil)
      case Defn.Val(_, pats, _, _) => Some(pats.flatMap(binders))
      case Defn.Var(_, pats, _, _) => Some(pats.flatMap(binders))
      case _: Defn => Some(Nil)
      case _: Import => Some(Nil)
      case _ => None
    }
  }

}
