package net.girkin.gomoku



import com.google.inject.AbstractModule
import users.{PsqlAnormUserStore, UserStore}

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
class MainModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[UserStore])
      .to(classOf[PsqlAnormUserStore])
  }
}
