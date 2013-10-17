package controllers

import play.api._
import scala.io.Source
import java.util.Random
import java.lang.ThreadLocal

import play.api.mvc._
import play.api.libs.ws._
import scala.concurrent._
import com.fasterxml.jackson.core._
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind._
import java.util._
import java.util.concurrent.ConcurrentHashMap
import play.api.libs.concurrent.Execution.Implicits._

object Application extends Controller {


  private val fileVector = {
     val fileLocation = "/tmp/wsmock_servers.txt"
     val source = Source.fromFile(fileLocation)
     source.getLines.toArray
  }

  private val localRandom = new ThreadLocal[Random] {
     override protected def initialValue() = new Random
  }

  private def chooseHost() = {
      fileVector(localRandom.get.nextInt(fileVector.length))
  }

  private def chooseURLBase() : String = {
      "http://" + chooseHost + ":8080/ws-backend-mock"
  }

  private def AURL(id : Int) : String = {
      chooseURLBase + "/mock.json?numItems=2&itemSize=50&delay=50&id=" + id
  }

  private def BURL(id: Int) : String = {
      chooseURLBase + "/mock.json?numItems=25&itemSize=30&delay=150&id=" + id
  }

  private def CURL(id: Int) : String = {
      chooseURLBase + "/mock.json?numItems=1&itemSize=5000&delay=80&id=" + id
  }

  private def DURL(id: Int) : String = {
      chooseURLBase + "/mock.json?numItems=1&itemSize=1000&delay=1&id=" + id
  }

  private def EURL(id : Int) : String = {
      chooseURLBase + "/mock.json?numItems=100&itemSize=30&delay=40&id=" + id
  }

  private def responseKey(thisRequest: Char, parsedAlready : ConcurrentHashMap[Char, HashMap[String, Object]], rs: Response) : Int = {
      parseResponse(thisRequest, parsedAlready, rs).get("responseKey").asInstanceOf[Int]
  }

  private def doProcessing(id: Int) = {

      // start a and b in parallel
      val aFuture = WS.url(AURL(id)).get()
      val bFuture = WS.url(BURL(id)).get()
      val parsedAlready = new ConcurrentHashMap[Char, HashMap[String, Object]]

      for (aResponse <- aFuture;
          bResponse <- bFuture;
          cResponse <-WS.url(CURL(responseKey('C', parsedAlready, aResponse))).get();
          dResponse <- WS.url(DURL(responseKey('D', parsedAlready, aResponse))).get();
          eResonse <- WS.url(EURL(responseKey('E', parsedAlready, bResponse))).get()) yield {
          Ok("done")
      }
  }


  private def parseResponse(thisRequest: Char, parsedAlready: ConcurrentHashMap[Char, HashMap[String, Object]], rs: Response) : HashMap[String, Object] = {
    val mapper = new ObjectMapper
    val existing = parsedAlready.get(thisRequest)
    if (existing != null) {
       existing
    }
    else {
        val typeRef = new TypeReference[HashMap[String, Object]]() {}
        val result = mapper.readValue(rs.body, typeRef)
        parsedAlready.putIfAbsent(thisRequest, result)
        result
    }
  }

  def testA = Action.async { request =>
     request.queryString.get("id") match  {
       case Some(id) => doProcessing(id.mkString.toInt)
       case None =>  Future { BadRequest("id not specified") }
     }
  }

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}