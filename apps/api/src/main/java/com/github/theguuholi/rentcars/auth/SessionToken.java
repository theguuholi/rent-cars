package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    description =
        "Signed access token. Send it in the Authorization header with the Bearer prefix.")
public record SessionToken(
    @Schema(description = "RS256 JWT, valid for 15 minutes.", format = "jwt") String accessToken) {}
