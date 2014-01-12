/*
 * Copyright (c) 2014 Andrey Gavrikov.
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

import com.neowit.utils.{ZipUtils, FileUtils, Logging, Config}
import java.io.{File, FileOutputStream}

class UnsupportedActionError(msg: String) extends Error(msg: String)

object ActionFactory {

    def getAction(session:Session, name: String): Option[Action] = {
        name match {
          case "refresh" => Some(new RefreshMetadata(session))
          case _ => throw new UnsupportedActionError(name + " is not supported")
        }


    }
}
trait Action extends Logging {
    def act
    def getConfig:Config
}
trait AsyncAction extends Action {
}

trait MetadataAction extends AsyncAction {

}

/**
 * 'refresh' action is 'retrieve' for all elements specified in package.xml
 *@param session - SFDC session
 */
case class RefreshMetadata(session: Session) extends MetadataAction {
    import com.sforce.soap.metadata.RetrieveRequest

    def getConfig:Config = session.getConfig

    def act {
        val retrieveRequest = new RetrieveRequest()
        retrieveRequest.setApiVersion(getConfig.apiVersion)
        setUpackaged(retrieveRequest)
        val retrieveResult = session.retrieve(retrieveRequest)
        updateFromRetrieve(retrieveResult)


    }
    def setUpackaged(retrieveRequest: RetrieveRequest) {
        val metaXml = new MetaXml(session.getConfig)
        val unpackagedManifest = metaXml.getPackageXml
        logger.debug("Manifest file: " + unpackagedManifest.getAbsolutePath)

        retrieveRequest.setUnpackaged(metaXml.getPackage)
    }
    /**
     * using ZIP file produced, for example, as a result of Retrieve operation
     * extract content and generate response file
     */
    def updateFromRetrieve(retrieveResult: com.sforce.soap.metadata.RetrieveResult) {
        val config = getConfig

        //val outputPath = appConfig.srcDir.getParentFile.getAbsolutePath
        //extract in temp area first
        val resultsFile = FileUtils.createTempFile("retrieveResult", ".zip")
        val out = new FileOutputStream(resultsFile)
        try {
            out.write(retrieveResult.getZipFile)
        } finally {
            out.close()
        }
        val tempFolder = FileUtils.createTempDir(config)
        val propertyByFilePath = new collection.mutable.HashMap[String,  com.sforce.soap.metadata.FileProperties]()
        try {
            val localDateByFName = ZipUtils.extract(resultsFile, tempFolder)
            //update session with file properties
            for (fileProp <- retrieveResult.getFileProperties) {
                val key = MetadataType.getKey(fileProp)
                val lastModifiedLocally = localDateByFName(fileProp.getFileName)
                val valueMap = MetadataType.getValueMap(fileProp) ++ Map("LocalMills" -> String.valueOf(lastModifiedLocally))
                session.setData(key, valueMap)

                propertyByFilePath.put(fileProp.getFileName, fileProp)
            }
        } finally {
            session.storeSessionData()
            resultsFile.delete()
        }
        config.responseWriter.println("RESULT=SUCCESS")
        config.responseWriter.println("file-count=" + propertyByFilePath.size)

    }

}
