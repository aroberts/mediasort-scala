package mediasort.io

import mediasort.config.Config.EmailConfig
import mediasort.strings._
import cats.syntax.either._
import java.util.Properties

import cats.effect.IO
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.Message.RecipientType
import javax.mail.{Session, Transport}


class Email(cfg: EmailConfig) {

  val session = Session.getDefaultInstance(new Properties() {
    put("mail.smtp.host", cfg.host.value)
    put("mail.smtp.port", cfg.port.value)
    put("mail.smtp.starttls.enable", cfg.tls.getOrElse(true))

  })

  def send(to: String, subject: String, body: String) = IO {
    Either.catchNonFatal {
      val message = new MimeMessage(session)
      message.setFrom(new InternetAddress(cfg.from.value))
      message.addRecipient(RecipientType.TO, new InternetAddress(to))
      message.setSubject(subject)
      message.setText(body)
      Transport.send(message, cfg.user.value, cfg.password.value)
    }.leftMap(e => scribe.error(s"[EMAIL] ${errorMessage(e)}"))
      .toOption
  }

}