package doobie.util

#+scalaz
import scalaz._
import Scalaz._
import doobie.util.capture.Capture
#-scalaz
#+cats
import cats.Monad
import cats.implicits._
import fs2.util.{ Catchable, Suspendable => Capture }
import fs2.interop.cats._
#-cats
import doobie.imports._
import org.specs2.mutable.Specification
import Predef._


object strategyspec extends Specification {

  val baseXa = DriverManagerTransactor[IOLite](
    "org.h2.Driver",
    "jdbc:h2:mem:queryspec;DB_CLOSE_DELAY=-1",
    "sa", ""
  )

  // an instrumented interpreter
  class Interp extends KleisliInterpreter[IOLite] {
    val M = implicitly[Monad[IOLite]]
    val C = implicitly[Capture[IOLite]]
    val K = implicitly[Catchable[IOLite]]

    object Connection {
      var autoCommit: Option[Boolean] = None
      var close:      Option[Unit]    = None
      var commit:     Option[Unit]    = None
      var rollback:   Option[Unit]    = None
    }

    object PreparedStatement {
      var close: Option[Unit] = None
    }

    object ResultSet {
      var close: Option[Unit] = None
    }

    override lazy val ConnectionInterpreter = new ConnectionInterpreter {
      override val close = delay(() => Connection.close = Some(())) *> super.close
      override val rollback = delay(() => Connection.rollback = Some(())) *> super.rollback
      override val commit = delay(() => Connection.commit = Some(())) *> super.commit
      override def setAutoCommit(b: Boolean) = delay(() => Connection.autoCommit = Option(b)) *> super.setAutoCommit(b)
    }

    override lazy val PreparedStatementInterpreter = new PreparedStatementInterpreter {
      override val close = delay(() => PreparedStatement.close = Some(())) *> super.close
    }

    override lazy val ResultSetInterpreter = new ResultSetInterpreter {
      override val close = delay(() => ResultSet.close = Some(())) *> super.close
    }

  }

  def xa(i: KleisliInterpreter[IOLite]) =
    Transactor.interpret.set(baseXa, i.ConnectionInterpreter)

  "Connection configuration and safety" >> {

    "Connection.autoCommit should be set to false" in {
      val i = new Interp
      sql"select 1".query[Int].unique.transact(xa(i)).unsafePerformIO
      i.Connection.autoCommit must_== Some(false)
    }

    "Connection.commit should be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].unique.transact(xa(i)).unsafePerformIO
      i.Connection.commit must_== Some(())
    }

    "Connection.commit should NOT be called on failure" in {
      val i = new Interp
      sql"abc".query[Int].unique.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.Connection.commit must_== None
    }

    "Connection.rollback should NOT be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].unique.transact(xa(i)).unsafePerformIO
      i.Connection.rollback must_== None
    }

    "Connection.rollback should be called on failure" in {
      val i = new Interp
      sql"abc".query[Int].unique.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.Connection.rollback must_== Some(())
    }

    "Connection.close should be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].unique.transact(xa(i)).unsafePerformIO
      i.Connection.close must_== Some(())
    }

    "Connection.close should be called on failure" in {
      val i = new Interp
      sql"abc".query[Int].unique.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.Connection.close must_== Some(())
    }

  }

  "Connection configuration and safety (streaming)" >> {

    "Connection.autoCommit should be set to false" in {
      val i = new Interp
      sql"select 1".query[Int].process.list.transact(xa(i)).unsafePerformIO
      i.Connection.autoCommit must_== Some(false)
    }

    "Connection.commit should be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].process.list.transact(xa(i)).unsafePerformIO
      i.Connection.commit must_== Some(())
    }

    "Connection.commit should NOT be called on failure" in {
      val i = new Interp
      sql"abc".query[Int].process.list.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.Connection.commit must_== None
    }

    "Connection.rollback should NOT be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].process.list.transact(xa(i)).unsafePerformIO
      i.Connection.rollback must_== None
    }

    "Connection.rollback should be called on failure" in {
      val i = new Interp
      sql"abc".query[Int].process.list.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.Connection.rollback must_== Some(())
    }

    "Connection.close should be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].process.list.transact(xa(i)).unsafePerformIO
      i.Connection.close must_== Some(())
    }

    "Connection.close should be called on failure" in {
      val i = new Interp
      sql"abc".query[Int].process.list.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.Connection.close must_== Some(())
    }

  }

  "PreparedStatement safety" >> {

    "PreparedStatement.close should be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].unique.transact(xa(i)).unsafePerformIO
      i.PreparedStatement.close must_== Some(())
    }

    "PreparedStatement.close should be called on failure" in {
      val i = new Interp
      sql"select 'x'".query[Int].unique.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.PreparedStatement.close must_== Some(())
    }

  }

  "PreparedStatement safety (streaming)" >> {

    "PreparedStatement.close should be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].process.list.transact(xa(i)).unsafePerformIO
      i.PreparedStatement.close must_== Some(())
    }

    "PreparedStatement.close should be called on failure" in {
      val i = new Interp
      sql"select 'x'".query[Int].process.list.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.PreparedStatement.close must_== Some(())
    }

  }

  "ResultSet safety" >> {

    "ResultSet.close should be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].unique.transact(xa(i)).unsafePerformIO
      i.ResultSet.close must_== Some(())
    }

    "ResultSet.close should be called on failure" in {
      val i = new Interp
      sql"select 'x'".query[Int].unique.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.ResultSet.close must_== Some(())
    }

  }

  "ResultSet safety (streaming)" >> {

    "ResultSet.close should be called on success" in {
      val i = new Interp
      sql"select 1".query[Int].process.list.transact(xa(i)).unsafePerformIO
      i.ResultSet.close must_== Some(())
    }

    "ResultSet.close should be called on failure" in {
      val i = new Interp
      sql"select 'x'".query[Int].process.list.transact(xa(i)).attempt.unsafePerformIO.toOption must_== None
      i.ResultSet.close must_== Some(())
    }

  }

}
