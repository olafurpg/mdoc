package mdoc.interfaces;

import java.util.List;
import java.nio.file.Path;
import coursierapi.Dependency;
import coursierapi.Repository;

public abstract class EvaluatedWorksheet {

  public abstract List<Diagnostic> diagnostics();
  public abstract List<EvaluatedWorksheetStatement> statements();

  public abstract List<String> scalacOptions();
  public abstract List<Path> classpath();
  public abstract List<Dependency> dependencies();
  public abstract List<Repository> repositories();

}
