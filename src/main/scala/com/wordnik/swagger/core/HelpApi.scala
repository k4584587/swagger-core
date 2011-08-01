package com.wordnik.swagger.core

/**
  * @author ayush
  * @since 6/23/11 12:49 PM
  *
  */


import scala.collection._
import mutable._
import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory
import util.TypeUtil
import org.apache.commons.lang.StringUtils
import javax.ws.rs.core.{UriInfo, HttpHeaders}
import java.rmi.server.Operation

class HelpApi {
  private val LOGGER = LoggerFactory.getLogger(classOf[HelpApi])

  var apiFilter: ApiAuthorizationFilter = null

  def this(apiFilterClassName: String) = {
    this ()
    if (apiFilterClassName != null) {
      try {
        apiFilter = Class.forName(apiFilterClassName).newInstance.asInstanceOf[ApiAuthorizationFilter]
      }
      catch {
        case e: ClassNotFoundException => LOGGER.error("Unable to resolve apiFilter class " + apiFilterClassName);
        case e: ClassCastException => LOGGER.error("Unable to cast to apiFilter class " + apiFilterClassName);
      }
    }
  }

  def filterDocs(doc: Documentation, headers: HttpHeaders, uriInfo: UriInfo, currentApiPath: String): Documentation = {
    //todo: apply auth and filter doc to only those which apply to current request/api-key
    if (apiFilter != null) {
      var apisToRemove = new ListBuffer[DocumentationEndPoint]
      doc.getApis().foreach(
          api => {
            if (api.getOperations() != null){
              var operationsToRemove = new ListBuffer[DocumentationOperation]
              api.getOperations().foreach( apiOperation  =>
                if (!apiFilter.authorize(api.path, apiOperation.httpMethod,  headers, uriInfo)) {
                  operationsToRemove += apiOperation
                }
              )
              for(operation <- operationsToRemove)api.removeOperation(operation)
              if(null == api.getOperations() || api.getOperations().size() == 0){
                apisToRemove + api
              }
            }
         }
      );
      for (api <- apisToRemove) doc.removeApi(api)
    }
    //todo: transform path?
    loadModel(doc)
    doc
  }

  private def loadModel(d: Documentation): Unit = {
    val directTypes = getReturnTypes(d)
    val types = TypeUtil.getReferencedClasses(directTypes)
    for (t <- types) {
      try {
        val clazz = Class.forName(t)
        val n = ApiPropertiesReader.read(clazz)
        if (null != n && null != n.getFields && n.getFields.length > 0) {
          d.addModel(n)
          d.addSchema(n.getName, n.toDocumentationSchema())
        }
      }
      catch {
        case e: ClassNotFoundException => LOGGER.error("Unable to resolve class " + t);
        case e: Exception => LOGGER.error("Unable to load model documentation for " + t, e)
      }
    }
  }

  private def getReturnTypes(d: Documentation): List[String] = {
    val l = new HashSet[String]
    if (d.getApis() != null) {
      //	endpoints
      for (n <- d.getApis()) {
        //	operations
        for (o <- n.getOperations()) {
          if (StringUtils.isNotBlank(o.getResponseTypeInternal())) l += o.getResponseTypeInternal().replaceAll("\\[\\]", "")
        }
      }
    }
    l.toList
  }

}