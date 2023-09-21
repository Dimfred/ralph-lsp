package org.alephium.ralph.lsp.server

import com.typesafe.scalalogging.StrictLogging
import org.alephium.ralph.lsp.compiler.CompilerAccess
import org.alephium.ralph.lsp.pc.completion.CodeCompleter
import org.alephium.ralph.lsp.pc.util.URIUtil
import org.alephium.ralph.lsp.pc.workspace.{Workspace, WorkspaceBuild, WorkspaceState}
import org.alephium.ralph.lsp.server.RalphLangServer._
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.jsonrpc.{messages, CompletableFutures, ResponseErrorException}
import org.eclipse.lsp4j.services._

import java.net.URI
import java.util
import java.util.concurrent.{CompletableFuture, Future => JFuture}

object RalphLangServer {

  /** Build capabilities supported by the LSP server */
  def serverCapabilities(): ServerCapabilities = {
    val capabilities = new ServerCapabilities()

    capabilities.setCompletionProvider(new CompletionOptions(true, util.Arrays.asList(".")))
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    capabilities.setDiagnosticProvider(new DiagnosticRegistrationOptions(true, true))

    capabilities
  }

  /** Start server with pre-configured client */
  def apply(client: RalphLangClient,
            listener: JFuture[Void])(implicit compiler: CompilerAccess): RalphLangServer = {
    val initialState =
      ServerState(
        client = Some(client),
        listener = Some(listener),
        workspace = None
      )

    new RalphLangServer(initialState)
  }
}

/**
 * The Ralph-LSP server.
 *
 * This class is the only one with mutable state in this repo.
 * All mutable state management occurs here.
 */
class RalphLangServer(@volatile private var state: ServerState = ServerState(None, None, None))(implicit compiler: CompilerAccess) extends LanguageServer with TextDocumentService with WorkspaceService with StrictLogging {

  def getState(): ServerState =
    this.state

  private def getClient(): RalphLangClient =
    state.client getOrElse {
      throw ResponseError.ClientNotConfigured.toResponseErrorException
    }

  private def setAndPublishState(state: ServerState): ServerState =
    this.synchronized {
      val newState = setState(state)
      // let the client know of workspace changes
      getClient().publish(state.workspace)
      newState
    }

  private def setState(state: ServerState): ServerState =
    this.synchronized {
      this.state = state
      state
    }

  /**
   * An initial call to this function is required before server is initialised.
   *
   * @param client   Client proxy instance provided by LSP4J.
   * @param listener LSP connection listener function.
   */
  def setInitialState(client: RalphLangClient,
                      listener: () => JFuture[Void]): Unit =
    this.synchronized {
      require(state.client.isEmpty, "Client is already set")
      require(state.listener.isEmpty, "Listener is already set")

      // Client must be set first, before running the request listener,
      // so that it is available for responding to requests.
      setState(state.copy(client = Some(client)))
      setState(state.copy(listener = Some(listener())))
    }

  // TODO: If allowed in this phase (maybe? the doc seem to indicate no), access the PresentationCompiler
  //       and do an initial workspace compilation.
  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] =
    CompletableFutures.computeAsync {
      cancelChecker =>
        // Previous commit uses the non-deprecated API but that does not work in vim.
        val rootURI =
          Option(params.getRootUri)
            .getOrElse(params.getRootPath)

        val workspaceURI =
          if (rootURI == null)
            throw ResponseError.WorkspaceFolderNotSupplied.toResponseErrorException
          else
            new URI(rootURI)

        val workspace =
          Workspace.create(workspaceURI)

        setState(state.updateWorkspace(workspace))

        cancelChecker.checkCanceled()

        new InitializeResult(serverCapabilities())
    }

  override def didOpen(params: DidOpenTextDocumentParams): Unit =
    didCodeChange(
      fileURI = new URI(params.getTextDocument.getUri),
      code = Some(params.getTextDocument.getText)
    )

  override def didChange(params: DidChangeTextDocumentParams): Unit =
    didCodeChange(
      fileURI = new URI(params.getTextDocument.getUri),
      code = Some(params.getContentChanges.get(0).getText)
    )

  override def didClose(params: DidCloseTextDocumentParams): Unit =
    didCodeChange(
      fileURI = new URI(params.getTextDocument.getUri),
      code = None
    )

  override def didSave(params: DidSaveTextDocumentParams): Unit =
    didCodeChange(
      fileURI = new URI(params.getTextDocument.getUri),
      code = None
    )

  /**
   * [[Workspace]] reacts to all code/build changes the same.
   *
   * @param fileURI File that changed.
   * @param code    Content of the file.
   */

  def didCodeChange(fileURI: URI,
                    code: Option[String]): Unit = {
    val fileExtension = URIUtil.getFileExtension(fileURI)
    if (fileExtension == WorkspaceBuild.BUILD_FILE_EXTENSION)
      buildChanged(
        fileURI = fileURI,
        code = code
      )
    else if (fileExtension == CompilerAccess.RALPH_FILE_EXTENSION)
      codeChanged(
        fileURI = fileURI,
        updatedCode = code
      )
    else
      throw
        getClient()
          .log(ResponseError.UnknownFile(fileURI))
          .toResponseErrorException
  }

  /**
   * Handles changes to the build valid.
   *
   * If the build file is valid, this drops existing compilations
   * and starts a fresh workspace.
   *
   * @param fileURI Location of the build file.
   * @param code    Build file's content.
   */
  def buildChanged(fileURI: URI,
                   code: Option[String]): Unit =
    this.synchronized {
      val fileName = URIUtil.getFileName(fileURI)

      if (fileName == WorkspaceBuild.BUILD_FILE_NAME)
        Workspace.build(
          buildURI = fileURI,
          build = code,
          state = getWorkspace()
        ) match {
          case Left(error) =>
            getClient().publishErrors(
              fileURI = fileURI,
              code = code, // TODO: the code here needs to be from the read build file.
              errors = List(error)
            )

          case Right(Some(newState)) =>
            // Build file changed.
            // Update the workspace and request the client for a full workspace build.
            setAndPublishState(state.updateWorkspace(newState))
            getClient().refreshDiagnostics() // request project wide re-build TODO: Handle future

          case Right(None) =>
            // Build file did not change.
            getClient().publishErrors(
              fileURI = fileURI,
              code = code,
              errors = List.empty
            )
        }
      else
        throw
          getClient()
            .log(ResponseError.InvalidBuildFileName(fileName))
            .toResponseErrorException
    }

  /**
   * Handles changes to ralph code files.
   *
   * @param fileURI     Location of the source file.
   * @param updatedCode Source changes
   */
  def codeChanged(fileURI: URI,
                  updatedCode: Option[String]): Unit =
    this.synchronized { // TODO: Remove synchronized. Use async.
      val workspace = getOrBuildWorkspace()
      setState(state.updateWorkspace(workspace))

      Workspace.codeChanged(
        fileURI = fileURI,
        updatedCode = updatedCode,
        currentState = workspace
      ) match {
        case Left(error) =>
          getClient().publishErrors(
            fileURI = fileURI,
            code = updatedCode, // TODO: Test if this code is the updated code.
            errors = List(error)
          )

        case Right(newState) =>
          // immediately set the new state with the updated code so it is available for other threads.
          setState(state.updateWorkspace(newState))

          // compile the new state.
          val compiledState =
            Workspace.parseAndCompile(newState)

          // set and publish the compiled state
          setAndPublishState(state.updateWorkspace(compiledState))
      }
    }

  override def completion(params: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] =
    CompletableFutures.computeAsync {
      cancelChecker =>
        val fileURI = new URI(params.getTextDocument.getUri)
        val line = params.getPosition.getLine
        val character = params.getPosition.getCharacter

        cancelChecker.checkCanceled()

        val workspace = getOrBuildWorkspace()
        setAndPublishState(state.updateWorkspace(workspace))

        val suggestions =
          CodeCompleter.complete(
            line = line,
            character = character,
            uri = fileURI,
            workspace = workspace
          )

        val completionList =
          DataConverter.toCompletionList(suggestions)

        cancelChecker.checkCanceled()

        messages.Either.forRight[util.List[CompletionItem], CompletionList](completionList)
    }

  /**
   * Returns existing workspace or initialises a new one from the configured build file.
   * Or else reports any workspace issues.
   *
   * @note Does not update the current state. The caller should set the new state.
   */
  def getOrBuildWorkspace(): WorkspaceState.BuildAware =
    this.synchronized { // TODO: Remove synchronized. Use async.
      state.workspace match {
        case Some(workspace) =>
          Workspace.getOrBuild(workspace) match {
            case Left(error) =>
              throw getClient().log(error)

            case Right(newState) =>
              newState
          }

        case None =>
          throw reportMissingWorkspace()
      }
    }

  def getWorkspace(): WorkspaceState =
    state.workspace match {
      case Some(workspace) =>
        workspace

      case None =>
        throw reportMissingWorkspace()
    }

  // Workspace folder is not defined.
  // This is not expected to occur since `initialized` is always invoked first.
  def reportMissingWorkspace(): ResponseErrorException =
    getClient()
      .log(ResponseError.WorkspaceFolderNotSupplied)
      .toResponseErrorException

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit =
    ()

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit =
    ()

  override def getTextDocumentService: TextDocumentService =
    this

  override def getWorkspaceService: WorkspaceService =
    this

  override def diagnostic(params: WorkspaceDiagnosticParams): CompletableFuture[WorkspaceDiagnosticReport] =
    CompletableFuture.completedFuture(new WorkspaceDiagnosticReport(util.Arrays.asList()))

  override def diagnostic(params: DocumentDiagnosticParams): CompletableFuture[DocumentDiagnosticReport] =
    CompletableFuture.completedFuture(new DocumentDiagnosticReport(new RelatedFullDocumentDiagnosticReport(util.Arrays.asList())))

  override def codeAction(params: CodeActionParams): CompletableFuture[util.List[messages.Either[Command, CodeAction]]] =
    CompletableFuture.completedFuture(util.Arrays.asList())

  override def resolveCompletionItem(unresolved: CompletionItem): CompletableFuture[CompletionItem] =
    CompletableFuture.completedFuture(unresolved)

  override def shutdown(): CompletableFuture[AnyRef] =
    state.listener match {
      case Some(listener) =>
        CompletableFuture.supplyAsync {
          () =>
            java.lang.Boolean.valueOf(listener.cancel(true))
        }

      case None =>
        CompletableFuture.failedFuture(new Exception("Listener not set"))
    }

  override def exit(): Unit =
    System.exit(0)

}
