package org.alephium.ralph.lsp.server

import org.alephium.ralph.lsp.compiler.CompilerAccess
import org.alephium.ralph.lsp.pc.PresentationCompiler
import org.alephium.ralph.lsp.pc.workspace.{WorkspaceConfig, WorkspaceState}
import org.alephium.ralph.lsp.server.RalphLangServer._
import org.eclipse.lsp4j._
import org.eclipse.lsp4j.jsonrpc.{messages, CompletableFutures}
import org.eclipse.lsp4j.services._

import java.net.URI
import java.util
import java.util.concurrent.CompletableFuture
import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success}

object RalphLangServer {

  /** Build capabilities supported by the LSP server */
  private def serverCapabilities(): ServerCapabilities = {
    val capabilities = new ServerCapabilities()

    capabilities.setCompletionProvider(new CompletionOptions(true, util.Arrays.asList(".")))
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full)
    capabilities.setDiagnosticProvider(new DiagnosticRegistrationOptions(true, true))

    capabilities
  }

}

/**
 * The Ralph-LSP server.
 *
 * @param state
 * @param ec
 * @param compiler
 */
class RalphLangServer(@volatile private var state: ServerState = ServerState())(implicit ec: ExecutionContext,
                                                                                compiler: CompilerAccess) extends LanguageServer with TextDocumentService with WorkspaceService {

  private def setState(state: ServerState): ServerState = {
    this.state = state
    state
  }

  private def setAndReportState(state: ServerState): ServerState = {
    this.setState(state)
    //    state.workspaceStates foreach {
    //      workspaceState =>
    //        state.withClient {
    //          implicit client =>
    //            RalphLangClient.publish(
    //              workspaceURI = workspaceConfig.workspaceURI,
    //              workspace = workspaceState
    //            )
    //        }
    //    }
    ???
  }

  /**
   * Follows the same [[LanguageClientAware.connect]].
   *
   * Only mutable function available to the outside world.
   *
   * @param client client-proxy used to communicate with the client.
   */
  def setClient(client: RalphLangClient): Unit =
    setState(state.copy(client = Some(client)))

  // TODO: If allowed in this phase (maybe? the doc seem to indicate no), access the PresentationCompiler
  //       and do an initial workspace compilation.
  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] =
    CompletableFutures.computeAsync {
      cancelChecker =>
        val workspaceStates =
          params.getWorkspaceFolders.asScala.to(ArraySeq).map {
            workspaceFolder =>
              val workspaceURI = new URI(workspaceFolder.getUri)
              WorkspaceState.UnConfigured(workspaceURI)
          }

        setState(state.copy(workspaceStates = Some(workspaceStates)))

        cancelChecker.checkCanceled()

        new InitializeResult(serverCapabilities())
    }

  override def didOpen(params: DidOpenTextDocumentParams): Unit =
    didCodeChange(
      fileURI = new URI(params.getTextDocument.getUri),
      updatedCode = Some(params.getTextDocument.getText)
    )

  override def didChange(params: DidChangeTextDocumentParams): Unit =
    didCodeChange(
      fileURI = new URI(params.getTextDocument.getUri),
      updatedCode = Some(params.getContentChanges.get(0).getText)
    )

  override def didClose(params: DidCloseTextDocumentParams): Unit =
    didCodeChange(
      fileURI = new URI(params.getTextDocument.getUri),
      updatedCode = None
    )

  override def didSave(params: DidSaveTextDocumentParams): Unit =
    didCodeChange(
      fileURI = new URI(params.getTextDocument.getUri),
      updatedCode = None
    )

  def didCodeChange(fileURI: URI,
                    updatedCode: Option[String]): Unit =
  //    state.withworkspaceConfig {
  //      workspaceConfig =>
  //        val (codeChangedState, serverStateVersion) =
  //          this.synchronized {
  //            val initialisedState =
  //              getOrInitWorkspaceState(workspaceConfig)
  //
  //            val codeChangedState =
  //              PresentationCompiler.codeChanged(
  //                fileURI = fileURI,
  //                updatedCode = updatedCode,
  //                currentState = initialisedState
  //              )
  //
  //            val newState = setState(state.copy(workspaceStates = Some(codeChangedState)))
  //            (codeChangedState, newState.version)
  //          }
  //
  //        val compiledState =
  //          PresentationCompiler.parsedAndCompileWorkspace(
  //            state = codeChangedState,
  //            compilerOptions = workspaceConfig.config.compilerOptions,
  //          )
  //
  //        //        this.synchronized {
  //        //          if (this.state.version == serverStateVersion)
  //        setAndReportState(
  //          workspaceConfig = workspaceConfig,
  //          state = this.state.copy(workspaceStates = Some(compiledState))
  //        )
  //      //        }
  //    }
    ???

  def getOrInitWorkspaceState(workspaceConfig: WorkspaceConfig): WorkspaceState =
  //    this.synchronized {
  //      state.workspaceStates match {
  //        case Some(oldState) =>
  //          oldState
  //
  //        case None =>
  //          val newWorkspaceState =
  //            PresentationCompiler.initialiseWorkspace(workspaceConfig.config) match {
  //              case Left(exception) =>
  //                throw state.withClient {
  //                  implicit client =>
  //                    RalphLangClient.log(exception)
  //                }
  //
  //              case Right(workspaceState) =>
  //                workspaceState
  //            }
  //
  //          state.withworkspaceConfig {
  //            workspaceConfig =>
  //              setAndReportState(
  //                workspaceConfig = workspaceConfig,
  //                state = state.copy(workspaceStates = Some(newWorkspaceState))
  //              )
  //          }
  //
  //          newWorkspaceState
  //      }
  //    }
    ???

  override def completion(params: CompletionParams): CompletableFuture[messages.Either[util.List[CompletionItem], CompletionList]] =
  //    CompletableFutures.computeAsync {
  //      cancelChecker =>
  //        val line = params.getPosition.getLine
  //        val character = params.getPosition.getCharacter
  //        val uri = new URI(params.getTextDocument.getUri)
  //
  //        cancelChecker.checkCanceled()
  //
  //        val suggestions =
  //          PresentationCompiler.complete(
  //            line = line,
  //            character = character,
  //            uri = uri,
  //            state = state.withworkspaceConfig(getOrInitWorkspaceState)
  //          )
  //
  //        val completionList =
  //          RalphLangClient.toCompletionList(suggestions)
  //
  //        cancelChecker.checkCanceled()
  //
  //        messages.Either.forRight[util.List[CompletionItem], CompletionList](completionList)
  //    }
    ???

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit =
    ()

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit =
    ()

  override def getTextDocumentService: TextDocumentService =
    this

  override def getWorkspaceService: WorkspaceService =
    this

  override def shutdown(): CompletableFuture[AnyRef] =
    CompletableFuture.completedFuture("TODO: shutdown")

  override def exit(): Unit =
    ()

}
