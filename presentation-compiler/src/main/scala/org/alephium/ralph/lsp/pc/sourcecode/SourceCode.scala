package org.alephium.ralph.lsp.pc.sourcecode

import org.alephium.ralph.lsp.access.compiler.CompilerAccess
import org.alephium.ralph.lsp.access.compiler.message.CompilerMessage
import org.alephium.ralph.lsp.access.file.FileAccess
import org.alephium.ralph.CompilerOptions
import org.alephium.ralph.lsp.pc.util.CollectionUtil._
import org.alephium.ralph.lsp.pc.util.URIUtil

import java.net.URI
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq

/**
 * Implements functions operating on source-code within a single file.
 */
private[pc] object SourceCode {

  /** Fetch all source files on disk */
  def initialise(sourceDirectory: URI)(implicit file: FileAccess): Either[CompilerMessage.AnyError, ArraySeq[SourceCodeState.OnDisk]] =
    file
      .list(sourceDirectory)
      .map(_.map(SourceCodeState.OnDisk).to(ArraySeq))

  /**
   * Synchronise source files with files on disk.
   *
   * When a workspace file structure is moved or renamed,
   * then in certain situations, the known source-files in memory are lost.
   * This ensures that all files on-disk are still known to the workspace.
   *
   * @param sourceDirectory Directory to synchronise with
   * @param sourceCode      Collection to add missing source files
   * @return Source files that are in-sync with files on disk.
   */
  def synchronise(sourceDirectory: URI,
                  sourceCode: ArraySeq[SourceCodeState])(implicit file: FileAccess): Either[CompilerMessage.AnyError, ArraySeq[SourceCodeState]] =
    initialise(sourceDirectory) map {
      onDiskFiles =>
        // clear code that is no within the source directory
        val directorySource =
          sourceCode filter {
            state =>
              URIUtil.contains(sourceDirectory, state.fileURI)
          }

        onDiskFiles.foldLeft(directorySource) {
          case (currentCode, onDisk) =>
            currentCode putIfEmpty onDisk
        }
    }

  /**
   * Parse a source file, given its current sate.
   *
   * @param sourceState Current state of the source code
   * @param compiler    Target compiler
   * @return New source code state
   */
  @tailrec
  def parse(sourceState: SourceCodeState)(implicit file: FileAccess,
                                          compiler: CompilerAccess): SourceCodeState =
    sourceState match {
      case SourceCodeState.UnCompiled(fileURI, code) =>
        compiler.parseContracts(code) match {
          case Left(error) =>
            SourceCodeState.ErrorSource(
              fileURI = fileURI,
              code = code,
              errors = Array(error),
              previous = None
            )

          case Right(parsedCode) =>
            SourceCodeState.Parsed(
              fileURI = fileURI,
              code = code,
              contracts = parsedCode,
            )
        }

      case onDisk: SourceCodeState.OnDisk =>
        getSourceCode(onDisk.fileURI) match {
          case errored: SourceCodeState.IsError =>
            errored

          case gotCode =>
            parse(gotCode)
        }

      case accessError: SourceCodeState.ErrorAccess =>
        // access the code from disk and parse it.
        getSourceCode(accessError.fileURI) match {
          case state: SourceCodeState.UnCompiled =>
            // successfully accessed the code, now parse it.
            parse(state)

          case failed: SourceCodeState.ErrorAccess =>
            // Failed again: Return the error so the client gets reported.
            failed
        }

      case parsed @ (_: SourceCodeState.Parsed | _: SourceCodeState.Compiled) =>
        parsed // code is already in parsed state, return the same state

      case error: SourceCodeState.ErrorSource =>
        // Code was already parsed and it errored.
        // Return the same state.
        error
    }

  /**
   * Compile a group of source-code files that are dependant on each other.
   *
   * @param sourceCode      Source-code to compile
   * @param compilerOptions Options to run for this compilation
   * @param compiler        Target compiler
   * @return Workspace-level error if an error occurred without a target source-file, or else next state for each source-code.
   */
  def compile(sourceCode: ArraySeq[SourceCodeState.Parsed],
              compilerOptions: CompilerOptions)(implicit compiler: CompilerAccess): Either[CompilerMessage.AnyError, ArraySeq[SourceCodeState.IsCodeAware]] = {
    val contractsToCompile =
      sourceCode.flatMap(_.contracts)

    val compilationResult =
      compiler.compileContracts(
        contracts = contractsToCompile,
        options = compilerOptions
      )

    SourceCodeStateBuilder.toSourceCodeState(
      parsedCode = sourceCode,
      compilationResult = compilationResult
    )
  }

  private def getSourceCode(fileURI: URI)(implicit file: FileAccess): SourceCodeState.IsAccessed =
    file.read(fileURI) match {
      case Left(error) =>
        SourceCodeState.ErrorAccess(fileURI, error)

      case Right(sourceCode) =>
        SourceCodeState.UnCompiled(fileURI, sourceCode)
    }

}
