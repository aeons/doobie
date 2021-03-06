#+scalaz
import doobie.enum.jdbctype
import doobie.imports._

import org.postgresql.util._
import scalaz.NonEmptyList

/**
 * The normal string mapping doesn't work for enums defined in another schema. Here we have
 *
 *   CREATE TYPE returns_data.return_status AS ENUM (
 *     'INITIAL',
 *     'IN_PROGRESS',
 *     'FINISHED'
 *   );
 *
 */
object OtherSchema {

  // Ok this mapping goes via String when reading and PGObject when writing, and it understands
  // when the type is reported as OTHER (schemaType).
  def wackyPostgresMapping(schemaName: String): Meta[String] =
    Meta.advanced[String](
      NonEmptyList(jdbctype.Other, jdbctype.VarChar),
      NonEmptyList(schemaName),
      (rs, n) => rs.getString(n),
      (ps, n, a) => {
        val o = new PGobject
        o.setValue(a.toString)
        o.setType(schemaName)
        ps.setObject(n, o)
      },
      (rs, n, a) => {
        val o = new PGobject
        o.setValue(a.toString)
        o.setType(schemaName)
        rs.updateObject(n, o)
      }
    )

  object ReturnStatus extends Enumeration {
    val INITIAL, IN_PROGRESS, FINISHED = Value
  }

  implicit val meta: Meta[ReturnStatus.Value] =
    wackyPostgresMapping(""""returns_data"."return_status"""").xmap(ReturnStatus.withName, _.toString)

  def main(args: Array[String]): Unit = {

    // Some setup
    val xa = Transactor.fromDriverManager[IOLite]("org.postgresql.Driver", "jdbc:postgresql:world", "postgres", "")
    val y  = xa.yolo
    import y._

    // Check as column value only
    val q1 = sql"SELECT 'INITIAL'::returns_data.return_status".query[ReturnStatus.Value]
    q1.check.unsafePerformIO
    q1.unique.quick.unsafePerformIO

    // Check as parameter too
    val q2 = sql"SELECT ${ReturnStatus.IN_PROGRESS}::returns_data.return_status".query[ReturnStatus.Value]
    q2.check.unsafePerformIO
    q2.unique.quick.unsafePerformIO

  }

}
#-scalaz
