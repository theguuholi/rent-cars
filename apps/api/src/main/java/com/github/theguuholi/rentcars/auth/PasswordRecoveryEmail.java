package com.github.theguuholi.rentcars.auth;

import com.github.theguuholi.rentcars.shared.mail.EmailMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
class PasswordRecoveryEmail {
  private final String webBaseUrl;
  private final String text;
  private final String html;

  PasswordRecoveryEmail(@Value("${rent.mail.web-base-url}") String webBaseUrl) throws IOException {
    this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    this.text =
        new ClassPathResource("mail/auth/password-recovery.txt")
            .getContentAsString(StandardCharsets.UTF_8);
    this.html =
        new ClassPathResource("mail/auth/password-recovery.html")
            .getContentAsString(StandardCharsets.UTF_8);
  }

  EmailMessage message(String email, String token) {
    String link = webBaseUrl + "/reset-password?token=" + token;
    return new EmailMessage(
        email,
        "Reset your Rent Cars password",
        text.replace("{{resetUrl}}", link),
        html.replace("{{resetUrl}}", link));
  }
}
