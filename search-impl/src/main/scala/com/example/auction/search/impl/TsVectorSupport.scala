package com.example.auction.search.impl

import java.sql.{PreparedStatement, ResultSet}

import slick.ast.Library.SqlOperator
import slick.ast.TypedType
import slick.jdbc.{JdbcProfile, JdbcType}
import slick.lifted.ExtensionMethods

import scala.reflect.ClassTag

trait TsVectorSupport {
  val profile: JdbcProfile

  import profile.api._

  case class TsVector(value: String)

  case class TsQuery(query: String)

  object TsVectorLibrary {
    val @@ = new SqlOperator("@@")
  }

  implicit lazy val simpleTsVectorTypeMapper: JdbcType[TsVector] = new UnmappableColumnType[TsVector]
  implicit lazy val simpleTsQueryTypeMapper: JdbcType[TsQuery] = new UnmappableColumnType[TsQuery]

  class TsVectorColumnExtensionMethods[P1](val c: Rep[P1])(implicit tm: JdbcType[TsVector], tm1: JdbcType[TsQuery]) extends ExtensionMethods[TsVector, P1] {

    override protected[this] implicit def b1Type: TypedType[TsVector] = implicitly[TypedType[TsVector]](tm)

    def @@[P2, R](e: Rep[P2])(implicit om: o#arg[TsQuery, P2]#to[Boolean, R]): Rep[R] =
      om.column(TsVectorLibrary.@@, n, e.toNode)
  }

  private class UnmappableColumnType[T: ClassTag] extends profile.DriverJdbcType[T] {
    override def sqlType: Int = ???
    override def setValue(v: T, p: PreparedStatement, idx: Int): Unit = ???
    override def getValue(r: ResultSet, idx: Int): T = ???
    override def updateValue(v: T, r: ResultSet, idx: Int): Unit = ???
  }

  implicit def simpleTsVectorOptionColumnExtensionMethods(c: Rep[Option[TsVector]]): TsVectorColumnExtensionMethods[Option[TsVector]] = {
    new TsVectorColumnExtensionMethods[Option[TsVector]](c)
  }

  lazy val toTsquery = SimpleFunction.unary[String, TsQuery]("to_tsquery")
}

