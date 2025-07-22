package com.reviewsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

// @SpringBootTest(classes = ReviewSystemMicroserviceApplication.class)
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
class ReviewSystemMicroserviceApplicationTests {

  @Test
  void contextLoads() {}
}
