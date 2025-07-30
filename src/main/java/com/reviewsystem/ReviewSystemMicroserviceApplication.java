package com.reviewsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.reviewsystem.repository")
public class ReviewSystemMicroserviceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ReviewSystemMicroserviceApplication.class, args);
  }
}
