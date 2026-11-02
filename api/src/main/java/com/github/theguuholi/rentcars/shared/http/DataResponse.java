package com.github.theguuholi.rentcars.shared.http;

/** JSON response envelope used by resources that follow the Elixir API contract. */
public record DataResponse<T>(T data) {}
