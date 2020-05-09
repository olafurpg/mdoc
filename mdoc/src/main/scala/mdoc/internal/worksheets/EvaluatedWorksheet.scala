package mdoc.internal.worksheets

import java.{util => ju}
import mdoc.{interfaces => i}
import java.nio.file.Path

case class EvaluatedWorksheet(
    val diagnostics: ju.List[i.Diagnostic],
    val statements: ju.List[i.EvaluatedWorksheetStatement],
    val scalacOptions: ju.List[String],
    val classpath: ju.List[Path],
    val dependencies: ju.List[coursierapi.Dependency],
    val repositories: ju.List[coursierapi.Repository]
) extends mdoc.interfaces.EvaluatedWorksheet
