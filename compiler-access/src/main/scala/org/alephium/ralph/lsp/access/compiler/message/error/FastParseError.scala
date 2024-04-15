// Copyright 2024 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see http://www.gnu.org/licenses/.

package org.alephium.ralph.lsp.access.compiler.message.error

import fastparse.Parsed
import org.alephium.ralph.error.CompilerError
import org.alephium.ralph.lsp.access.compiler.message.CompilerMessage

object FastParseError {

  @inline def apply(failure: Parsed.Failure): FastParseError =
    FastParseError(CompilerError.FastParseError(failure))

}

/**
 * Stores error produced by `FastParse`.
 *
 * [[CompilerError.FastParseError]] also contains other error data, such as [[CompilerError.FastParseError.tracedMsg]]
 * which can be used for better error reports to the client.
 */
case class FastParseError(error: CompilerError.FastParseError) extends CompilerMessage.FormattedError
