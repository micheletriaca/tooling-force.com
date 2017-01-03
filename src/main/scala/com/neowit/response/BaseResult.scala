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

package com.neowit.response

import java.io.File

import com.neowit.apex.ProcessedTestFailure
import com.neowit.apex.actions._
import com.neowit.apex.parser.Member
import com.neowit.utils.JsonSupport
import spray.json._

/**
  * Author: Andrey Gavrikov
  * Date: 01/01/2017
  */
sealed abstract class BaseResult {

    def addToExisting(result: BaseResult): BaseResult = {
        // by default do nothing
        this
    }
    def addToExisting(result: ActionResult): BaseResult = {
        // by default do nothing
        this
    }
}

case class FindSymbolResult(memberOpt: Option[Member]) extends BaseResult with JsonSupport {
    def toJson: JsValue = memberOpt match {
        case Some(member) => member.serialise
        case None => Map.empty.toJson
    }
}

case class ListCompletionsResult(members: List[Member]) extends BaseResult

case class ListModifiedResult(modified: List[File], deleted: List[File]) extends BaseResult
case class RefreshMetadataResult(modified: List[File]) extends BaseResult
case class CheckSyntaxResult(sourceFile: File, errors: List[SyntaxError]) extends BaseResult
case class RunTestsResult(
                             testFailures: List[ProcessedTestFailure],
                             logFilePathByClassName: Map[String, String] = Map.empty,
                             log: Option[File] = None,
                             coverageReportOpt: Option[CodeCoverageReport] = None,
                             deploymentFailureReport: Option[DeploymentFailureReport] = None
                         ) extends BaseResult
case class DeployAllDestructiveResult(deploymentResult: DeploymentReport, diffReport: Option[DiffWithRemoteReport]) extends BaseResult
case class DeployModifiedDestructiveResult(deploymentResultOpt: Option[DeploymentReport]) extends BaseResult
case class DeployModifiedResult(deploymentReport: DeploymentReport) extends BaseResult
case class DeployAllResult(deploymentResult: DeploymentReport) extends BaseResult
case class ListConflictingResult(conflictReport: DeploymentConflictsReport) extends BaseResult