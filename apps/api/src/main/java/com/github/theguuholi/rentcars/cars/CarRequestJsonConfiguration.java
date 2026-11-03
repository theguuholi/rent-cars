package com.github.theguuholi.rentcars.cars;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
class CarRequestJsonConfiguration implements WebMvcConfigurer {
  @Override
  public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
    builder.configureMessageConverters(
        converter -> {
          if (converter instanceof JacksonJsonHttpMessageConverter json) {
            var mapper = carRequestMapper(json.getMapper());
            json.registerMappersForType(
                CarRequest.class,
                types -> json.getSupportedMediaTypes().forEach(type -> types.put(type, mapper)));
          }
        });
  }

  private static JsonMapper carRequestMapper(JsonMapper base) {
    return base.rebuild()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .changeDefaultNullHandling(ignored -> JsonSetter.Value.construct(Nulls.FAIL, Nulls.FAIL))
        .withCoercionConfig(
            LogicalType.Textual,
            config -> {
              config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
              config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
              config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            })
        .build();
  }
}
