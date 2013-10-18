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

  val responseKey = "responseKey"
  val delay = "delay"
  val itemSize = "itemSize"
  val numItems = "numItems"
  val items = "items"

  private def responseKey(rs: Response) : Int = {
      parseResponse(rs).get(responseKey).asInstanceOf[Int]
  }

  private def buildDelayStat(name: String, jsonMap: HashMap[String, Object]) : HashMap[String, Int] = {
      val map = new HashMap[String, Int]
      map.put(name, jsonMap.get(delay).asInstanceOf[Int])
      map
  }

  private def buildNumItemsStat(name: String, jsonMap: HashMap[String, Object]) : HashMap[String, Int] = {
      val map = new HashMap[String, Int]
      map.put(name, jsonMap.get(numItems).asInstanceOf[Int])
      map
  }

  private def buildItemSizeStat(name: String, jsonMap: HashMap[String, Object]) : HashMap[String, Int] = {
     val map = new HashMap[String, Int]
     map.put(name, jsonMap.get(itemSize).asInstanceOf[Int])
     map
  }

  private def buildItem(name: String, jsonMap: HashMap[String, Object]) : HashMap[String, HashMap[String, Object]] = {
     val map = new HashMap[String, HashMap[String, Object]]
     map.put(name, jsonMap)
     map
  }

  private def doProcessing(id: Int) = {

      // start a and b in parallel
      val aFuture = WS.url(AURL(id)).get()
      val bFuture = WS.url(BURL(id)).get()

      for (aResponse <- aFuture;
          bResponse <- bFuture;
          cResponse <-WS.url(CURL(responseKey(aResponse))).get();
          dResponse <- WS.url(DURL(responseKey(aResponse))).get();
          eResponse <- WS.url(EURL(responseKey(bResponse))).get()) yield {

          val aJSON = parseResponse(aResponse)
          val bJSON = parseResponse(bResponse)
          val cJSON = parseResponse(cResponse)
          val dJSON = parseResponse(dResponse)
          val eJSON = parseResponse(eResponse)

          // ported from ServiceResponseBuilder.java
          val sumResponse = cJSON.get(responseKey).asInstanceOf[Int] + dJSON.get(responseKey).asInstanceOf[Int] + eJSON.get(responseKey).asInstanceOf[Int]
          val resultMap = new HashMap[String, Object]
          resultMap.put(responseKey, sumResponse.asInstanceOf[Object])
          val delayList = new ArrayList[HashMap[String, Int]]
          delayList.add(buildDelayStat("a", aJSON))
          delayList.add(buildDelayStat("b", bJSON))
          delayList.add(buildDelayStat("c", cJSON))
          delayList.add(buildDelayStat("d", dJSON))
          delayList.add(buildDelayStat("e", eJSON))
          resultMap.put(delay, delayList.asInstanceOf[Object])

          val numItemList = new ArrayList[HashMap[String, Int]]
          numItemList.add(buildNumItemsStat("a", aJSON))
          numItemList.add(buildNumItemsStat("b", bJSON))
          numItemList.add(buildNumItemsStat("c", cJSON))
          numItemList.add(buildNumItemsStat("d", dJSON))
          numItemList.add(buildNumItemsStat("e", eJSON))
          resultMap.put(numItems, numItemList.asInstanceOf[Object])

          val itemSizeList = new ArrayList[HashMap[String, Int]]
          itemSizeList.add(buildItemSizeStat("a", aJSON))
          itemSizeList.add(buildItemSizeStat("b", bJSON))
          itemSizeList.add(buildItemSizeStat("c", cJSON))
          itemSizeList.add(buildItemSizeStat("d", dJSON))
          itemSizeList.add(buildItemSizeStat("e", eJSON))
          resultMap.put(itemSize, itemSizeList.asInstanceOf[Object])

          val itemList = new ArrayList[HashMap[String, HashMap[String, Object]]]
          itemList.add(buildItem("a", aJSON))
          itemList.add(buildItem("b", bJSON))
          itemList.add(buildItem("c", cJSON))
          itemList.add(buildItem("d", dJSON))
          itemList.add(buildItem("e", eJSON))
          resultMap.put(items, itemList)

          val mapper = new ObjectMapper
          Ok(mapper.writeValueAsString(resultMap))
      }
  }

  class HashTypeReference extends TypeReference[HashMap[String, Object]] {}

  private def parseResponse(rs: Response) : HashMap[String, Object] = {
    val mapper = new ObjectMapper


    val typeRef = new HashTypeReference
    mapper.readValue(rs.body, typeRef)
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