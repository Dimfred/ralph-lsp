package org.alephium.ralph.lsp.server

import fastparse.IndexedParserInput
import org.alephium.ralph.lsp.access.compiler.message.CompilerMessage
import org.alephium.ralph.lsp.pc.completion.Suggestion
import org.alephium.ralph.lsp.pc.sourcecode.SourceCodeState
import org.alephium.ralph.lsp.pc.workspace.WorkspaceState
import org.alephium.ralph.SourcePosition
import org.eclipse.lsp4j._

import java.net.URI
import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

/** Implements functions that transform internal types to LSP4J types */
object DataConverter {

  /** Convert Ralph's FormattableError to lsp4j's Diagnostic */
  def toDiagnostic(code: Option[String],
                   message: CompilerMessage,
                   severity: DiagnosticSeverity): Diagnostic = {
    val range =
      code match {
        case Some(code) =>
          val fastParseLineNumber = IndexedParserInput(code).prettyIndex(message.index.index)
          val sourcePosition = SourcePosition.parse(fastParseLineNumber)

          val start = new Position(sourcePosition.rowIndex, sourcePosition.colIndex)
          val end = new Position(sourcePosition.rowIndex, sourcePosition.colIndex + message.index.width)
          new Range(start, end)

        case None =>
          // If source-code text is not known, then the line-number can't be fetched.
          // So return this error at file-level with an empty range.
          new Range(new Position(0, 0), new Position(0, 0))
      }

    new Diagnostic(range, message.message, severity, "Ralph")
  }

  def toWorkspaceDiagnostics(workspace: WorkspaceState.SourceAware): PublishDiagnosticsParams = {
    val workspaceDiagnostics =
      workspace match {
        case compiled: WorkspaceState.Errored =>
          compiled.workspaceErrors map {
            error =>
              // These are workspace level errors such as `Compiler.Error`, their source-code information is unknown.
              toDiagnostic(
                code = None,
                message = error,
                severity = DiagnosticSeverity.Error
              )
          }

        case _ =>
          Seq.empty
      }

    new PublishDiagnosticsParams(workspace.workspaceURI.toString, workspaceDiagnostics.asJava)
  }

  def toSourceCodeDiagnostics(state: WorkspaceState.SourceAware): Iterable[PublishDiagnosticsParams] =
    state.sourceCode collect {
      case state: SourceCodeState.ErrorSource =>
        // transform multiple source code errors to diagnostics.
        val diagnostics =
          state.errors map {
            error =>
              toDiagnostic(
                code = Some(state.code),
                message = error,
                severity = DiagnosticSeverity.Error
              )
          }

        new PublishDiagnosticsParams(state.fileURI.toString, diagnostics.asJava)

      case state: SourceCodeState.ErrorAccess =>
        // transform single source code access error to diagnostics.
        val diagnostics =
          toDiagnostic(
            code = None,
            message = state.error,
            severity = DiagnosticSeverity.Error
          )

        new PublishDiagnosticsParams(state.fileURI.toString, util.Arrays.asList(diagnostics))

      case state: SourceCodeState.Compiled =>
        // transform source code warning messages to diagnostics.
        val diagnostics =
          state.warnings map {
            warning =>
              toDiagnostic(
                code = Some(state.code),
                message = warning,
                severity = DiagnosticSeverity.Warning
              )
          }

        new PublishDiagnosticsParams(state.fileURI.toString, diagnostics.asJava)
    }

  def toPublishDiagnostics(workspace: WorkspaceState.SourceAware): Iterable[PublishDiagnosticsParams] = {
    val sourceCodeDiagnostics = toSourceCodeDiagnostics(workspace)
    val workspaceDiagnostics = toWorkspaceDiagnostics(workspace)
    sourceCodeDiagnostics ++ Seq(workspaceDiagnostics)
  }

  def toPublishDiagnostics(fileURI: URI,
                           code: Option[String],
                           errors: List[CompilerMessage.AnyError],
                           severity: DiagnosticSeverity): PublishDiagnosticsParams = {
    val diagnostics =
      errors map {
        error =>
          toDiagnostic(
            code = code,
            message = error,
            severity = severity
          )
      }

    new PublishDiagnosticsParams(fileURI.toString, diagnostics.asJava)
  }

  /**
   * Builds diagnostics to publish, clearing older resolved errors or warnings.
   *
   * @param previousState Oldest state
   * @param nextState     Newer state.
   *                      Set to [[None]] if previousState is the only state.
   * @return Diagnostics to publish.
   */
  def toPublishDiagnotics(previousState: WorkspaceState.SourceAware,
                          nextState: Option[WorkspaceState.SourceAware]): Iterable[PublishDiagnosticsParams] = {
    val previousDiagnostics =
      toPublishDiagnostics(previousState)

    nextState match {
      case Some(nextState) =>
        val nextDiagnostics =
          toPublishDiagnostics(nextState)

        val resolvedDiagnostics =
          previousDiagnostics.foldLeft(Seq.empty[PublishDiagnosticsParams]) {
            case (resolved, previous) =>
              nextDiagnostics.find(_.getUri == previous.getUri) match {
                case Some(_) =>
                  // next diagnostics contains messages for this URI.
                  resolved

                case None =>
                  // next diagnostics does not contain messages for this URI, create an entry to clear it.
                  val resolvedDiagnostics = new PublishDiagnosticsParams(previous.getUri, util.Arrays.asList())
                  resolved :+ resolvedDiagnostics
              }
          }

        resolvedDiagnostics ++ nextDiagnostics

      case None =>
        previousDiagnostics
    }
  }

  def toCompletionList(suggestions: Array[Suggestion]): CompletionList = {
    val items = new util.ArrayList[CompletionItem]()

    suggestions foreach {
      suggestion =>
        val item = new CompletionItem()
        item.setLabel(suggestion.label)
        item.setDetail(suggestion.detail)
        item.setDocumentation(suggestion.documentation)
        item.setInsertText(suggestion.insert)
        item.setKind(CompletionItemKind.valueOf(suggestion.productPrefix))
        items.add(item)
    }

    new CompletionList(items)
  }

}
