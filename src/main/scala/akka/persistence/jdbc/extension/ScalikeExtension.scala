package akka.persistence.jdbc.extension

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.persistence.jdbc.common.PluginConfig
import scalikejdbc.{DataSourceConnectionPool, AutoSession, ConnectionPool}

object ScalikeExtension extends ExtensionId[ScalikeExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ScalikeExtensionImpl = new ScalikeExtensionImpl(system)

  override def lookup() = ScalikeExtension
}

class ScalikeExtensionImpl(system: ExtendedActorSystem) extends Extension {
  implicit val session = AutoSession
  val cfg = PluginConfig(system)

  cfg.datasourceJndi match {
    case Some(name) =>
      import javax.naming._
      import javax.sql._

      val ds = (new InitialContext)
        .lookup("java:/comp/env").asInstanceOf[Context]
        .lookup(name).asInstanceOf[DataSource]

      ConnectionPool.singleton(new DataSourceConnectionPool(ds))
      ConnectionPool.add('akkaPersistenceJdbc, new DataSourceConnectionPool(ds))

    case None =>
      Class.forName(cfg.driverClassName)
      ConnectionPool.singleton(cfg.url, cfg.username, cfg.password)
      ConnectionPool.add('akkaPersistenceJdbc, cfg.url, cfg.username, cfg.password)
  }
}
