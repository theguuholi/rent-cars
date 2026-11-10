package com.github.theguuholi.rentcars.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {
  @Test
  @DisplayName(
      "Given a message with text and HTML, when sent, then SMTP receives both alternatives")
  void sendsBothFormats() throws Exception {
    var mail = mock(JavaMailSender.class);
    var mime = new MimeMessage(Session.getInstance(new Properties()));
    when(mail.createMimeMessage()).thenReturn(mime);
    new SmtpEmailSender(mail, "no-reply@example.test")
        .send(new EmailMessage("driver@example.test", "Reset", "Plain link", "<p>HTML link</p>"));
    verify(mail).send(mime);
    var bytes = new ByteArrayOutputStream();
    mime.writeTo(bytes);
    assertThat(bytes.toString())
        .contains(
            "no-reply@example.test",
            "driver@example.test",
            "text/plain",
            "text/html",
            "Plain link",
            "HTML link");
  }

  @Test
  @DisplayName(
      "Given an invalid recipient, when composing the email, then a mail exception is raised without sending")
  void invalidAddress() {
    var mail = mock(JavaMailSender.class);
    when(mail.createMimeMessage())
        .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    var sender = new SmtpEmailSender(mail, "no-reply@example.test");
    assertThatThrownBy(
            () -> sender.send(new EmailMessage("bad address", "Reset", "Text", "<p>HTML</p>")))
        .isInstanceOf(MailSendException.class);
    verify(mail, never()).send(any(MimeMessage.class));
  }
}
