package com.github.theguuholi.rentcars.cars;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Reads the precomputed ranking; an unavailable ranking must not prevent browsing the fleet. */
@Component
class PopularCarsClient {
  private static final Logger LOG = LoggerFactory.getLogger(PopularCarsClient.class);
  private final RestClient client;

  PopularCarsClient(
      RestClient.Builder builder,
      @Value("${rent.recommendations.url}") String url,
      @Value("${rent.recommendations.connect-timeout}") Duration connectTimeout,
      @Value("${rent.recommendations.read-timeout}") Duration readTimeout) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(connectTimeout);
    requests.setReadTimeout(readTimeout);
    client = builder.baseUrl(url).requestFactory(requests).build();
  }

  List<UUID> carIds() {
    try {
      var ranking =
          client.get().uri("/recommendations/cars?limit=100").retrieve().body(Ranking.class);
      if (ranking == null) return List.of();
      return ranking.items().stream().map(Item::carId).toList();
    } catch (RestClientException exception) {
      LOG.warn("Popularity ranking unavailable; returning no recommendations", exception);
      return List.of();
    }
  }

  record Ranking(List<Item> items) {
    Ranking {
      items = List.copyOf(items);
    }
  }

  record Item(UUID carId) {
    Item {
      Objects.requireNonNull(carId);
    }
  }
}
