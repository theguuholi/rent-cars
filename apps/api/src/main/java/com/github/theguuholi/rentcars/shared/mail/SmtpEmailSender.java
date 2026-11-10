package com.github.theguuholi.rentcars.shared.mail;

import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class SmtpEmailSender implements EmailSender {
  private final JavaMailSender mail;
  private final String from;

  SmtpEmailSender(JavaMailSender mail, @Value("${rent.mail.from}") String from) {
    this.mail = mail;
    this.from = from;
  }

  @Override
  public void send(EmailMessage message) {
    var mime = mail.createMimeMessage();
    try {
      var helper = new MimeMessageHelper(mime, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(message.to());
      helper.setSubject(message.subject());
      helper.setText(message.text(), message.html());
    } catch (MessagingException exception) {
      throw new MailSendException("Could not prepare email", exception);
    }
    mail.send(mime);
  }
}
