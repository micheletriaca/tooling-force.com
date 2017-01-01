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

package com.neowit.apex

import java.util.Properties

import com.neowit.apex.parser.ApexParserUtils
import com.neowit.apex.parser.antlr.{SoqlParser, SoqlLexer}
import com.neowit.utils.FileUtils
import org.antlr.v4.runtime._
import org.scalatest.FunSuite

class SoqlGrammarTest extends FunSuite{

    val is = getClass.getClassLoader.getResource("paths.properties").openStream()
    val paths = new Properties()
    paths.load(is)

    val projectPath = paths.getProperty("sql-grammar.projectPath")
    test("Test Soql.g4 grammar") {
        val testFilePath = projectPath + "/SOQL-Grammar-Test.sql"
        testGrammarInFile(testFilePath)
    }
    /**
     * Soql.g4 grammar tester
     * main method which does actual validations
     * @param soqlFilePath - full path to the file where test SOQL code examples
     */
    private def testGrammarInFile(soqlFilePath: String): Unit = {

        val lines = FileUtils.readFile(soqlFilePath).getLines().toArray[String]
        var i = 0
        while (i < lines.length) {
            val line = lines(i)
            var soqlLines: String = ""
            if (line.contains("--#START: ")) {
                val testName = line.substring("--#START: ".length)
                i = i + 1
                val soqlLine = i
                while (!lines(i).contains("--#END")) {
                    val soqlLine = lines(i)
                    soqlLines += " " + soqlLine
                    i = i + 1
                }
                //reached end of current test description, now run the test
                runSingleQueryGrammarTest(testName, soqlLines, soqlLine)
            }
            i += 1

        }
    }

    private def runSingleQueryGrammarTest(description: String, soqlStatement: String, line: Int): Unit = {
        val tokens = new CommonTokenStream(getLexer(soqlStatement))
        val parser = new SoqlParser(tokens)
        ApexParserUtils.removeConsoleErrorListener(parser)
        parser.setErrorHandler(new ErrorCatcher())
        parser.setBuildParseTree(false)
        try {
            parser.soqlStatement()
        } catch {
            case ex: RecognitionException =>
                val msg = s"""Failed to parse test: $description line:$line  \n$soqlStatement \n """
                assert(false, msg)
            case ex: Throwable => assert(false, "Failed to parse: " + description + "\n" + soqlStatement + "; exception=" + ex)
        }

    }
    private def getLexer(soqlStatement: String): SoqlLexer = {
        val input = new ANTLRInputStream(soqlStatement)
        val lexer = new SoqlLexer(input)
        lexer
    }

    class ErrorCatcher extends DefaultErrorStrategy {
        override def reportError(recognizer: Parser, e: RecognitionException): Unit = {
            //assert(false, e.getMessage)
            throw e
        }
    }
}
