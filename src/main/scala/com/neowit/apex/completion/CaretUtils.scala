/*
 * Copyright (c) 2017 Andrey Gavrikov.
 * this file is part of tooling-force.com application
 * https://github.com/neowit/tooling-force.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.neowit.apex.completion

import java.io.File

import com.neowit.apex.parser.ApexParserUtils
import org.antlr.v4.runtime._
import org.antlr.v4.runtime.misc.IntervalSet

abstract class Caret(val line:  Int, val startIndex: Int) {
    private var tokenType: String = ""

    def getOffset: Int

    def setType(path: String): Unit = {
        tokenType = path
    }

    def getType: String = {
        tokenType
    }

    def equals(node: Token): Boolean = {
        line == node.getLine && startIndex == node.getCharPositionInLine
    }
}

class CaretInFile(line:  Int, startIndex: Int, file: File) extends Caret (line, startIndex){
    //make sure that offset is calculated once and not recalculated on each access
    lazy val getOffset: Int =  ApexParserUtils.getOffset(file, line, startIndex)
}
/**
 *
 * used to stop parser at current cursor position
 */
class CodeCompletionTokenSource( source: TokenSource, caret: Caret) extends TokenSource {

    private var tokenFactory: TokenFactory[_] = CommonTokenFactory.DEFAULT
    private var caretToken: Token = null
    private val tokenFactorySourcePair = new misc.Pair(source, source.getInputStream)

    private val caretOffset = caret.getOffset
    private var prevToken: Token = null

    def getLine: Int = {
        source.getLine
    }

    def getCharPositionInLine: Int = {
        source.getCharPositionInLine
    }

    def getInputStream: CharStream = {
        source.getInputStream
    }

    def getSourceName: String = {
        source.getSourceName
    }
    def nextToken: Token = {
        if (null == caretToken) {
            var token: Token = source.nextToken
            //println("caretAToken=" + token.toString)
            if (token.getStopIndex + 1 < caretOffset) {
                //before target caretAToken
                prevToken = token

            } else if (token.getStartIndex > caretOffset) {
                //caretAToken = CaretToken(tokenFactorySourcePair, Token.DEFAULT_CHANNEL, caretOffset, caretOffset)
                //found target?
                //caretToken = prevToken
                val t = CaretToken2(tokenFactorySourcePair, Token.DEFAULT_CHANNEL, caretOffset, caretOffset)
                t.setCaret(caret)
                t.setOriginalToken(token)
                t.prevToken = prevToken
                token = t
                caretToken = token
            } else {
                if (token.getStopIndex + 1 == caretOffset && token.getStopIndex >= token.getStartIndex) {
                    if (!ApexParserUtils.isWordToken(token)) {
                        return token
                    }
                }
                val t = CaretToken2(token)
                t.setCaret(caret)
                t.prevToken = prevToken
                token = t
                caretToken = token
            }
            return token
        }

        throw new UnsupportedOperationException("Attempted to look past the caret.")
    }
    def getTokenFactory: TokenFactory[_] = {
        tokenFactory
    }

    def setTokenFactory(tokenFactory: TokenFactory[_]): Unit = {
        source.setTokenFactory(tokenFactory)
        this.tokenFactory = if (tokenFactory != null) tokenFactory else CommonTokenFactory.DEFAULT
    }
}
class CaretReachedException(val recognizer: Parser, val finalContext: RuleContext, val caretToken: CaretTokenTrait, val cause: Option[RecognitionException] = None )
    extends RuntimeException {

    def getInputStream: CommonTokenStream = cause match {
        case Some(exception) => exception.getInputStream.asInstanceOf[CommonTokenStream]
        case None =>
            recognizer.getInputStream.asInstanceOf[CommonTokenStream]
    }
}

class CompletionErrorStrategy extends DefaultErrorStrategy {

    override def reportError(recognizer: Parser, e: RecognitionException): Unit = {
        if (e != null && e.getOffendingToken != null &&
            e.getOffendingToken.getType == CaretToken2.CARET_TOKEN_TYPE) {
            return
        }
        super.reportError(recognizer, e)
    }


    override def recover(recognizer: Parser, e: RecognitionException): Unit = {
        if (e != null && e.getOffendingToken != null) {
            if (e.getOffendingToken.getType == CaretToken2.CARET_TOKEN_TYPE) {
                throw new CaretReachedException(recognizer, recognizer.getContext, e.getOffendingToken.asInstanceOf[CaretTokenTrait], Some(e))
            } else if (e.getInputStream.index() + 1 <= e.getInputStream.size() &&
                e.getInputStream.asInstanceOf[CommonTokenStream].LT(2).getType == CaretToken2.CARET_TOKEN_TYPE) {
                throw new CaretReachedException(recognizer, recognizer.getContext, e.getInputStream.asInstanceOf[CommonTokenStream].LT(2).asInstanceOf[CaretTokenTrait], Some(e))
            }
        }
        super.recover(recognizer, e)
    }
    override def consumeUntil(recognizer: Parser, set: IntervalSet): Unit = {
        super.consumeUntil(recognizer, set)
    }

    override def recoverInline(recognizer: Parser): Token = {
        if (recognizer.getInputStream.LA(1) == CaretToken2.CARET_TOKEN_TYPE) {
            throw new CaretReachedException(recognizer, recognizer.getContext, recognizer.getInputStream.LT(1).asInstanceOf[CaretTokenTrait])
        }
        super.recoverInline(recognizer)
    }

    override def singleTokenInsertion(recognizer: Parser): Boolean = {
        if (recognizer.getInputStream.LA(1) == CaretToken2.CARET_TOKEN_TYPE) {
            return false
        }
        super.singleTokenInsertion(recognizer)
    }

    override def singleTokenDeletion(recognizer: Parser): Token = {
        if (recognizer.getInputStream.LA(1) == CaretToken2.CARET_TOKEN_TYPE) {
            return null
        }
        super.singleTokenDeletion(recognizer)
    }

    override def sync(recognizer: Parser): Unit = {
        if (recognizer.getInputStream.LA(1) == CaretToken2.CARET_TOKEN_TYPE) {
            return
        }
        super.sync(recognizer)
    }
}

object CaretToken2 {
    final val CARET_TOKEN_TYPE: Int = -2
    def apply(tokenFactorySourcePair: misc.Pair[TokenSource, CharStream], channel: Int, start: Int, stop: Int) = {
        new org.antlr.v4.runtime.CommonToken(tokenFactorySourcePair, CaretToken2.CARET_TOKEN_TYPE, channel, start, stop) with CaretTokenTrait
    }

    def apply(oldToken: Token) = {
        val token = new org.antlr.v4.runtime.CommonToken(oldToken) with CaretTokenTrait
        token.setType(CaretToken2.CARET_TOKEN_TYPE)
        token.setOriginalToken(oldToken)
        token
    }
}
trait CaretTokenTrait extends org.antlr.v4.runtime.CommonToken {

    private var originalToken: Option[Token] = None

    def setOriginalToken(token: Token): Unit = {
        originalToken = Some(token)
    }
    def getOriginalToken: Option[Token] = {
        originalToken
    }

    def getOriginalType: Int = {
        originalToken match {
          case Some(token) => token.getType
          case None => - 1
        }
    }
    var prevToken: Token = null

    private var caret: Option[Caret] = None

    def setCaret(caret: Caret): Unit = {
        this.caret = Some(caret)
    }

    def getCaretPositionInLine:Int = caret match {
      case Some(_caret) => _caret.startIndex
      case None => -1
    }

    def getCaretLine:Int = caret match {
        case Some(_caret) => _caret.line
        case None => -1
    }

    def getCaretOffset:Int = caret match {
        case Some(_caret) => _caret.getOffset
        case None => -1
    }

    def getCaret: Option[Caret] = caret

    /**
     * make sure that for token which parser put into Caret position we
     * do not return text of the token that starts *after* caret or ends *before* caret
     */
    override def getText: String = {

        getOriginalToken match {
          case Some(token) =>
              if (this.getCaretOffset >= 0 && token.getStartIndex < this.getCaretOffset && (token.getStopIndex +1) >= this.getCaretOffset) {
                  super.getText
              } else {
                  ""
              }
          case None =>
                  ""
        }
    }

}

