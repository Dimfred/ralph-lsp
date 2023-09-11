package org.alephium.ralph.lsp.pc.workspace

import org.alephium.ralph.error.CompilerError.FormattableError
import org.alephium.ralph.lsp.pc.sourcecode.SourceCodeState

import java.net.URI
import scala.collection.immutable.ArraySeq

sealed trait WorkspaceState

object WorkspaceState {

  sealed trait Configured extends WorkspaceState {
    def config: WorkspaceConfig

    /** A workspace contains multiple source files */
    def sourceCodeStates: ArraySeq[SourceCodeState]

    /** Add or update the source file */
    def updateOrAdd(newState: SourceCodeState): ArraySeq[SourceCodeState] = {
      val index = sourceCodeStates.indexWhere(_.fileURI == newState.fileURI)
      if (index >= 0)
        sourceCodeStates.updated(index, newState)
      else
        sourceCodeStates appended newState
    }
  }

  case class UnConfigured(workspaceURI: URI) extends WorkspaceState

  /** State: Source files are un-compiled or partially-compiled */
  case class UnCompiled(config: WorkspaceConfig,
                        sourceCodeStates: ArraySeq[SourceCodeState]) extends WorkspaceState.Configured

  /** State: All source files parsed, therefore can be compiled */
  case class Parsed(config: WorkspaceConfig,
                    sourceCodeStates: ArraySeq[SourceCodeState.Parsed]) extends WorkspaceState.Configured

  /**
   * Result of a compilation run.
   *
   * @param sourceCodeStates New valid source code states.
   * @param workspaceErrors  Project/workspace level errors
   * @param previousState    Previous valid parsed state
   */
  case class Compiled(sourceCodeStates: ArraySeq[SourceCodeState],
                      workspaceErrors: ArraySeq[FormattableError],
                      previousState: WorkspaceState.Parsed) extends WorkspaceState.Configured {
    def config: WorkspaceConfig =
      previousState.config
  }

}
