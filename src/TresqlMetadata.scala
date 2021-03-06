package querease

import java.io.File

import org.tresql.compiling.CompilerFunctions
import org.tresql.compiling.CompilerMetaDataFactory
import org.tresql.MetaData
import org.tresql.metadata.Col
import org.tresql.metadata.Key
import org.tresql.metadata.{ Ref => TresqlRef }
import org.tresql.metadata.Table
import org.tresql.metadata.TypeMapper

import mojoz.metadata.io._
import mojoz.metadata.in._
import mojoz.metadata.Type
import mojoz.metadata.TableDef.{ TableDefBase => TableDef }
import mojoz.metadata.ColumnDef.{ ColumnDefBase => ColumnDef }

class TresqlMetadata(
  val tableDefs: Seq[TableDef[ColumnDef[Type]]],
  val procedureMetadata: MetaData)
  extends MetaData with TypeMapper {

  val tables = tableDefs.map { td =>
    def toTresqlCol(c: ColumnDef[Type]) = {
      val typeName = c.type_.name
      val scalaType = xsd_scala_type_map(typeName)
      val jdbcTypeCode = 0 // unknown, not interested
      Col(c.name, c.nullable, jdbcTypeCode, scalaType)
    }
    val name = td.name
    val cols = td.cols.map(toTresqlCol).toList
    val key = Key(td.pk.map(_.cols.toList) getOrElse Nil)
    val refs = td.refs.groupBy(_.refTable)
      .mapValues(_.map(r => TresqlRef(r.cols.toList, r.refCols.toList)).toList)
    Table(name, cols, key, refs)
  }.map(t => (t.name, t)).toMap

  override def table(name: String) = tables(name)
  override def tableOption(name: String) = tables.get(name)
  override def procedure(name: String) =
    if (procedureMetadata != null) procedureMetadata.procedure(name)
    else sys.error("Procedure metadata not initialized")
  override def procedureOption(name: String) =
    if (procedureMetadata != null) procedureMetadata.procedureOption(name)
    else None

  lazy val tableMetadataString = {
    def colToString(col: ColumnDef[Type]) =
      col.name +
        (if (!col.nullable) " !" else "") +
        " " + col.type_.name
    def refToString(cols: Seq[String], refTableName: String, refCols: Seq[String]) =
      cols.mkString(", ") + " -> " + refTableName + refCols.mkString("(", ", ", ")")
    import scala.language.implicitConversions
    implicit def stringToSeq(s: String): Seq[String] = Seq(s)
    def tableToString(table: Table, mojozTable: TableDef[ColumnDef[Type]]) =
      Seq[Seq[String]](
        "table: " + table.name,
        "columns:",
        mojozTable.cols.map("- " + colToString(_)),
        (if (table.key.cols.size > 0) "pk: " + table.key.cols.mkString(", ") else Nil),
        (if (table.rfs.size > 0) "refs:" else Nil),
        table.rfs.toSeq.flatMap(tr =>
          tr._2.map(r => "- " + refToString(r.cols, tr._1, r.refCols)))
      ).flatten.mkString("\n")
    tableDefs.map(t => tableToString(tables(t.name), t)).mkString("\n\n") + "\n"
  }
}

class TresqlMetadataFactory extends CompilerMetaDataFactory {
  /** Creates tresql compiler metadata by loading table metadata (as produced by
   *  [[querease.TresqlMetadata.tableMetadataString]]) and instantiating
   *  function signatures class.
   *
   *  Expects "tableMetadataFile" and "functions" keys in conf parameter.
   *  Defaults to "tresql-table-metadata.yaml" and "org.tresql.compiling.Functions"
   */
  override def create(conf: Map[String, String]) = {
    val tableMetadataFileName = conf.getOrElse("tableMetadataFile", "tresql-table-metadata.yaml")
    val functionSignaturesClassName = conf.getOrElse("functions", "org.tresql.compiling.Functions")
    val rawTableMetadata = YamlMd.fromFile(new File(tableMetadataFileName))
    val mdConventions = new SimplePatternMdConventions(Nil, Nil, Nil)
    val tableDefs = new YamlTableDefLoader(rawTableMetadata, mdConventions).tableDefs
    val procedureMetadata = null // TODO?
    if (functionSignaturesClassName == null)
      new TresqlMetadata(tableDefs, procedureMetadata)
    else {
      val f = Class.forName(functionSignaturesClassName)
      new TresqlMetadata(tableDefs, procedureMetadata) with CompilerFunctions {
        override def compilerFunctions = f
      }
    }
  }
}
