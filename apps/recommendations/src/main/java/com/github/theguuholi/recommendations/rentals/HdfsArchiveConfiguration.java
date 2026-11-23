package com.github.theguuholi.recommendations.rentals;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rent.archive.enabled", havingValue = "true")
class HdfsArchiveConfiguration {
  @Bean
  RentalEventArchive rentalEventArchive(
      ObjectMapper json,
      @Value("${rent.archive.url}") String url,
      @Value("${rent.archive.user}") String user,
      @Value("${rent.archive.root}") String root) {
    var requests = new SimpleClientHttpRequestFactory();
    requests.setConnectTimeout(3000);
    requests.setReadTimeout(5000);
    var client = RestClient.builder().baseUrl(url).requestFactory(requests).build();
    return new RentalEventArchive(new HdfsArchiveClient(client, user), json, root);
  }
}
