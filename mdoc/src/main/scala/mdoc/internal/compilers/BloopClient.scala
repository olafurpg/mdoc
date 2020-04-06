package mdoc.internal.compilers

import java.nio.channels.Pipe
import java.nio.channels.Channels
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import org.eclipse.lsp4j.jsonrpc.JsonRpcException
import scala.concurrent.Promise
import bloop.launcher.LauncherMain
import java.nio.charset.StandardCharsets
import bloop.bloopgun.core.Shell
import mdoc.internal.BuildInfo
import bloop.launcher.LauncherStatus.FailedToConnectToServer
import bloop.launcher.LauncherStatus.FailedToInstallBloop
import bloop.launcher.LauncherStatus.FailedToOpenBspConnection
import bloop.launcher.LauncherStatus.FailedToParseArguments
import bloop.launcher.LauncherStatus.SuccessfulRun
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.jsonrpc.Launcher
import ch.epfl.scala.bsp4j.BuildServer
import ch.epfl.scala.bsp4j.ScalaBuildServer
import ch.epfl.scala.bsp4j.BuildClient
import scala.concurrent.ExecutionContext
import ch.epfl.scala.bsp4j.LogMessageParams
import ch.epfl.scala.bsp4j.TaskStartParams
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams
import ch.epfl.scala.bsp4j.ShowMessageParams
import ch.epfl.scala.bsp4j.DidChangeBuildTarget
import ch.epfl.scala.bsp4j.TaskFinishParams
import ch.epfl.scala.bsp4j.TaskProgressParams
import java.util.concurrent.ConcurrentLinkedQueue
import mdoc.internal.compilers.BloopClient.MdocBuildClient
import mdoc.internal.compilers.BloopClient.BloopBuildServer
import ch.epfl.scala.bsp4j.InitializeBuildParams
import scala.meta.io.AbsolutePath
import ch.epfl.scala.bsp4j.BuildClientCapabilities
import java.{util => ju}
import ch.epfl.scala.bsp4j.InitializeBuildResult
import ch.epfl.scala.bsp4j.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.io.PrintWriter

class BloopClient(
    val client: MdocBuildClient,
    val server: BloopBuildServer,
    val initialize: InitializeBuildResult,
    val close: () => Unit
)

object BloopClient {
  def create(target: AbsolutePath): BloopClient = {
    val launcherInOutPipe = Pipe.open()
    val launcherIn = new QuietInputStream(
      Channels.newInputStream(launcherInOutPipe.source()),
      "Bloop InputStream"
    )
    val clientOut = new ClosableOutputStream(
      Channels.newOutputStream(launcherInOutPipe.sink()),
      "Bloop OutputStream"
    )
    val clientInOutPipe = Pipe.open()
    val clientIn = Channels.newInputStream(clientInOutPipe.source())
    val launcherOut = Channels.newOutputStream(clientInOutPipe.sink())
    val serverStarted = Promise[Unit]()
    val main = new LauncherMain(
      launcherIn,
      launcherOut,
      System.err,
      StandardCharsets.UTF_8,
      Shell.default,
      userNailgunHost = None,
      userNailgunPort = None,
      serverStarted
    )

    ExecutionContext.global.execute(() =>
      main.runLauncher(
        BuildInfo.bloopVersion,
        skipBspConnection = false,
        serverJvmOptions = Nil
      )
    )
    Await.result(serverStarted.future, Duration(1, TimeUnit.MINUTES))
    val tracePath = target.resolve("bsp.trace.json")
    val fos = Files.newOutputStream(
      tracePath.toNIO,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING // don't append infinitely to existing file
    )
    val tracer = new PrintWriter(fos)
    val localClient = new MdocBuildClient
    val launcher = new Launcher.Builder[BloopBuildServer]()
      .traceMessages(tracer)
      .setOutput(clientOut)
      .setInput(clientIn)
      .setLocalService(localClient)
      .setRemoteInterface(classOf[BloopBuildServer])
      // .setExecutorService(ec)
      .create()
    val listening = launcher.startListening()
    val server = launcher.getRemoteProxy
    val initializeResult = server
      .buildInitialize(
        new InitializeBuildParams(
          "MDoc",
          BuildInfo.version,
          BuildInfo.bspVersion,
          target.toURI.toString(),
          new BuildClientCapabilities(ju.Collections.singletonList("scala"))
        )
      )
      .get()
    server.onBuildInitialized()
    new BloopClient(localClient, server, initializeResult, close = () => {
      listening.cancel(false)
    })
  }

  trait BloopBuildServer extends BuildServer with ScalaBuildServer
  class MdocBuildClient extends BuildClient {
    val diagnostics = new ConcurrentLinkedQueue[PublishDiagnosticsParams]
    import scala.collection.JavaConverters._
    def hasDiagnosticSeverity(severity: DiagnosticSeverity): Boolean =
      diagnostics.asScala
        .exists(_.getDiagnostics().asScala.exists(_.getSeverity() == severity))
    override def onBuildShowMessage(params: ShowMessageParams): Unit = ()
    override def onBuildLogMessage(params: LogMessageParams): Unit = ()
    override def onBuildTaskStart(params: TaskStartParams): Unit = ()
    override def onBuildTaskProgress(params: TaskProgressParams): Unit = ()
    override def onBuildTaskFinish(params: TaskFinishParams): Unit = ()
    override def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = {
      diagnostics.add(params)
      pprint.log(params)
    }
    override def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = ()
  }

  private class QuietInputStream(underlying: InputStream, name: String)
      extends FilterInputStream(underlying) {
    override def read(): Int = {
      try {
        underlying.read()
      } catch {
        case e: IOException =>
          -1
      }
    }
  }

  private class ClosableOutputStream(underlying: OutputStream, name: String)
      extends FilterOutputStream(underlying) {
    var isClosed = false
    override def flush(): Unit = {
      try {
        if (!isClosed) {
          super.flush()
        }
      } catch {
        case _: IOException =>
      }
    }
    override def write(b: Int): Unit = {
      try {
        if (!isClosed) {
          underlying.write(b)
        }
      } catch {
        // IOException is usually thrown when the stream is closed
        case e @ (_: IOException | _: JsonRpcException) =>
          isClosed = true
          throw e
      }
    }
  }
}
