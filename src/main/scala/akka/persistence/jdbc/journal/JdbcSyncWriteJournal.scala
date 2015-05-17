package akka.persistence.jdbc.journal

import akka.actor.ActorLogging
import akka.persistence._
import akka.persistence.jdbc.common.ActorConfig
import akka.persistence.jdbc.journal.RowTypeMarkers._
import akka.persistence.journal.SyncWriteJournal

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

trait JdbcSyncWriteJournal extends SyncWriteJournal with ActorLogging with ActorConfig with JdbcStatements {

  implicit def executionContext: ExecutionContext

  override def writeMessages(messages: Seq[PersistentRepr]): Unit = insertMessages(messages)

  override def deleteMessagesTo(processorId: String, toSequenceNr: Long, permanent: Boolean): Unit = {
    log.debug(s"deleteMessagesTo for processorId: $processorId to sequenceNr: $toSequenceNr, permanent: $permanent")

     permanent match {
       case true => deleteMessageRange(processorId, toSequenceNr)
       case false => (1 to toSequenceNr.toInt).toList.map(_.toLong).foreach { sequenceNr =>
         selectMessage(processorId, sequenceNr).foreach { msg =>
           updateMessage(processorId, sequenceNr, DeletedMarker, msg.update(deleted = true))
         }
       }
     }
  }

  override def asyncReadHighestSequenceNr(processorId: String, fromSequenceNr: Long): Future[Long] = {
    log.debug(s"Async read for highest sequence number for processorId: [$processorId] (hint, seek from  nr: [$fromSequenceNr])")
    selectMaxSequenceNr(processorId)
  }

  override def asyncReplayMessages(processorId: String, fromSequenceNr: Long, toSequenceNr: Long, max: Long)(replayCallback: (PersistentRepr) => Unit): Future[Unit] = {
    log.debug(s"Async replay for processorId [$processorId], from sequenceNr: [$fromSequenceNr], to sequenceNr: [$toSequenceNr] with max records: [$max]")

    Future[Unit] {
      selectMessagesFor(processorId, fromSequenceNr, toSequenceNr, max).foreach(replayCallback)
    }
  }
}
