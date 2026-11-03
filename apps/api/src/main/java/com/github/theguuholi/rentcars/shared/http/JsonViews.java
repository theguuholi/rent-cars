package com.github.theguuholi.rentcars.shared.http;

/** The detail response includes every summary field. */
public final class JsonViews {
  private JsonViews() {}

  public interface Summary {}

  public interface Detail extends Summary {}
}
