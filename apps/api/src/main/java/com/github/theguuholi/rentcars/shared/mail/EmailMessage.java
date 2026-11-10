package com.github.theguuholi.rentcars.shared.mail;

/** One outgoing message with a plain-text fallback for clients that cannot render HTML. */
public record EmailMessage(String to, String subject, String text, String html) {}
