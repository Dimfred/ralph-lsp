package org.alephium.ralph.lsp.pc.sourcecode.error

import org.alephium.ralph.SourceIndex
import org.alephium.ralph.lsp.access.compiler.message.{CompilerMessage, SourceIndexExtra}

import java.net.URI

case class SourceCodeAccessFailed(uri: URI) extends CompilerMessage.Error {
  override def message: String =
    s"Source code errored on access. URI: $uri"

  override def index: SourceIndex =
    SourceIndexExtra.zero(uri)
}
