package org.alephium.ralph.lsp.pc.workspace.build

import org.alephium.ralph.lsp.compiler.error.StringError
import org.alephium.ralph.lsp.pc.util.FileIO
import org.alephium.ralph.lsp.pc.workspace.build.error._
import org.alephium.ralph.lsp.pc.workspace.build.BuildState._
import org.alephium.ralph.SourceIndex
import org.alephium.ralph.error.CompilerError.FormattableError
import org.alephium.ralph.lsp.pc.util.SourceIndexUtil._
import org.alephium.ralphc.Config

import java.net.URI
import java.nio.file.{Path, Paths}
import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success}

/** Implements functions for validating `build.ralph` */
object BuildValidator {

  /** Validate and promotes a parsed build-file to compiled */
  def validate(parsed: BuildParsed): BuildState.Compiled = {

    /** Returns a successful compiled state */
    def success() = {
      val (_, absoluteContractPath, absoluteArtifactPath) =
        getAbsolutePaths(parsed)

      BuildCompiled(
        buildURI = parsed.buildURI,
        code = parsed.code,
        config = Config(
          compilerOptions = parsed.config.compilerOptions,
          contractPath = absoluteContractPath,
          artifactPath = absoluteArtifactPath
        )
      )
    }

    // Run validation checks
    validatePathsInWorkspace(parsed)
      .orElse(validatePathsExists(parsed))
      .getOrElse(success())
  }

  /** Checks that buildURI is in the project's root directory */
  def validateBuildURI(buildURI: URI,
                       workspaceURI: URI): Either[ErrorInvalidBuildFileLocation, URI] =
    if (Paths.get(buildURI).getParent != Paths.get(workspaceURI)) // Build file must be in the root workspace folder.
      Left(
        ErrorInvalidBuildFileLocation(
          buildURI = buildURI,
          workspaceURI = workspaceURI
        )
      )
    else
      Right(buildURI)

  /** Validate that the configured paths are within the workspace directory */
  private def validatePathsInWorkspace(parsed: BuildParsed): Option[BuildState.BuildErrored] = {
    val contractPath = parsed.config.contractPath
    val artifactPath = parsed.config.artifactPath

    // absolute source paths
    val (workspacePath, absoluteContractPath, absoluteArtifactPath) =
      getAbsolutePaths(parsed)

    val errors =
      ListBuffer.empty[FormattableError]

    // Validate: is the contract path with the workspace
    if (!absoluteContractPath.startsWith(workspacePath))
      errors addOne
        ErrorDirectoryOutsideWorkspace(
          dirPath = contractPath,
          index =
            SourceIndex(
              index = parsed.code.lastIndexOf(contractPath), // TODO: lastIndexOf is temporary solution until an AST is available.
              width = contractPath.length
            ).ensureNotNegative()
        )

    // Validate: is the artifact path with the workspace
    if (!absoluteArtifactPath.startsWith(workspacePath))
      errors addOne
        ErrorDirectoryDoesNotExists(
          dirPath = artifactPath,
          index =
            SourceIndex(
              index = parsed.code.lastIndexOf(artifactPath), // TODO: lastIndexOf is temporary solution until an AST is available.
              width = artifactPath.length
            ).ensureNotNegative()
        )

    // Check if errors exists
    if (errors.isEmpty)
      None
    else
      Some(
        BuildErrored( // report errors
          buildURI = parsed.buildURI,
          code = Some(parsed.code),
          errors = ArraySeq.from(errors)
        )
      )
  }

  /** Validate that the configured paths exist within the workspace directory */
  private def validatePathsExists(parsed: BuildParsed): Option[BuildState.BuildErrored] = {
    val contractPath = parsed.config.contractPath
    val artifactPath = parsed.config.artifactPath

    // absolute source paths
    val (workspacePath, absoluteContractPath, absoluteArtifactPath) =
      getAbsolutePaths(parsed)

    // do these paths exists with the workspace directory?
    val compileResult =
      for {
        contractExists <- FileIO.exists(absoluteContractPath)
        artifactsExists <- FileIO.exists(absoluteArtifactPath)
      } yield (contractExists, artifactsExists)

    compileResult match {
      case Success((contractExists, artifactsExists)) =>
        val errors =
          ListBuffer.empty[FormattableError]

        // check if contract path exists
        if (!contractExists)
          errors addOne
            ErrorDirectoryDoesNotExists(
              dirPath = contractPath,
              index =
                SourceIndex(
                  index = parsed.code.lastIndexOf(contractPath), // TODO: lastIndexOf is temporary solution until an AST is available.
                  width = contractPath.length
                ).ensureNotNegative()
            )

        // check if artifact path exists
        if (!artifactsExists)
          errors addOne
            ErrorDirectoryDoesNotExists(
              dirPath = artifactPath,
              index =
                SourceIndex(
                  index = parsed.code.lastIndexOf(artifactPath), // TODO: lastIndexOf is temporary solution until an AST is available.
                  width = artifactPath.length
                ).ensureNotNegative()
            )

        // check if errors exists
        if (errors.isEmpty) {
          None // No errors!
        } else {
          val errors =
            BuildErrored( // report errors
              buildURI = parsed.buildURI,
              code = Some(parsed.code),
              errors = ArraySeq.from(errors)
            )

          Some(errors)
        }

      case Failure(exception) =>
        // exception occurred performing IO.
        val errors =
          BuildErrored(
            buildURI = parsed.buildURI,
            code = Some(parsed.code),
            errors = ArraySeq(StringError(exception.getMessage))
          )

        Some(errors)
    }
  }

  /**
   * Returns absolute paths of the build/config file.
   *
   * @return A 3-tuple `(workspacePath, absoluteContractPath, absoluteArtifactPath)`
   */
  private def getAbsolutePaths(parsed: BuildParsed): (Path, Path, Path) = {
    val workspacePath = Paths.get(parsed.workspaceURI)
    val absoluteContractPath = workspacePath.resolve(parsed.config.contractPath)
    val absoluteArtifactPath = workspacePath.resolve(parsed.config.artifactPath)
    (workspacePath, absoluteContractPath, absoluteArtifactPath)
  }

}
