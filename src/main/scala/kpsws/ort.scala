package kpsws

import org.tresql.ORT
import org.tresql.NameMap
import xsdgen.ElementName

import scala.collection.JavaConversions._

object ort extends org.tresql.NameMap {

  def pojoToMap(pojo: Any):Map[String, _] = pojo.getClass.getMethods filter (m => m.getName.startsWith("get")
      && m.getParameterTypes.size == 0) map (m => m.getName.drop(3) -> (m.invoke(pojo) match {
        case x if (isPrimitive(x)) => x
        case l:Seq[_] => l map (pojoToMap(_))
        case l:Array[_] => l map (pojoToMap(_))
        case l:java.util.Collection[_] => l map (pojoToMap(_))
        case x => pojoToMap(x)
      })) toMap

  def mapToPojo(map: Map[String, _], pojo: Any) = map foreach (
      t => pojo.getClass.getMethods.filter(m => m.getName == "set" + t._1) match {
    case Array(x) => t._2 match {
      case d: BigDecimal => {
        val t = x.getParameterTypes()(0)
        if (t == classOf[Double] || t == classOf[java.lang.Double]) x.invoke(pojo, d.doubleValue.asInstanceOf[Object])
        else if (t == classOf[java.math.BigDecimal]) x.invoke(pojo, d.bigDecimal)
      }
      case _ => x.invoke(pojo, t._2.asInstanceOf[Object])
    }
    case _ =>
  })
  
  def isPrimitive[T](x: T)(implicit evidence: T <:< AnyVal = null) = evidence != null || (x match {
    case _:java.lang.Number | _:java.lang.Boolean | _:java.util.Date => true
    case _ => false
  })

  private def xsdNameToDbName(xsdName: String) = {
    // FIXME DUPLICATE CODE WITH XSDGEN PROJECT
    // FIXME DUPLICATE CLASS ElementName WITH XSDGEN PROJECT
    ElementName.get(xsdName).split("\\-").map(_.toLowerCase match {
      case "user" => "usr"
      case "group" => "grp"
      case "role" => "rle"
      case x => x
    }).mkString("_") match {
      case x if x endsWith "_2" => x.replace("_2", "2") // XXX dirty fix phone_2
      case x => x
    }
  }

  def save(tableName: String, pojo: AnyRef, id: Long) =
    ORT.save(tableName,
      pojoToMap(pojo).map(e => (xsdNameToDbName(e._1), e._2)) + ("id" -> id))

  def db_ws_name_map(ws: Map[String, _]) = ws.map(t => t._1.toLowerCase -> t._1)

}