package com.reviewsystem.testcontainers;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/** Utility class for creating and configuring LocalStack containers for testing */
public class LocalStackTestContainer {

  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:4.6.0");

  private static final String DEFAULT_REGION = "us-east-1";
  private static final String DEFAULT_ACCESS_KEY = "test";
  private static final String DEFAULT_SECRET_KEY = "test";

  /**
   * Creates a configured LocalStack container for S3 testing
   *
   * @return Configured LocalStack container
   */
  public static LocalStackContainer create() {
    return new LocalStackContainer(LOCALSTACK_IMAGE)
        .withServices(S3)
        .withEnv("DEFAULT_REGION", DEFAULT_REGION)
        .withEnv("AWS_DEFAULT_REGION", DEFAULT_REGION)
        .withEnv("AWS_ACCESS_KEY_ID", DEFAULT_ACCESS_KEY)
        .withEnv("AWS_SECRET_ACCESS_KEY", DEFAULT_SECRET_KEY)
        .withEnv("EDGE_PORT", "4566")
        .withEnv("SERVICES", "s3")
        .withEnv("DEBUG", "1")
        .withEnv("DATA_DIR", "/tmp/localstack/data")
        .withEnv("HOST_TMP_FOLDER", "/tmp/localstack")
        .withReuse(false); // Ensure fresh container for each test
  }

  /**
   * Creates a LocalStack container with custom configuration
   *
   * @param services Additional services to enable
   * @return Configured LocalStack container
   */
  public static LocalStackContainer createWithServices(LocalStackContainer.Service... services) {
    LocalStackContainer.Service[] allServices =
        new LocalStackContainer.Service[services.length + 1];
    allServices[0] = S3;
    System.arraycopy(services, 0, allServices, 1, services.length);

    return new LocalStackContainer(LOCALSTACK_IMAGE)
        .withServices(allServices)
        .withEnv("DEFAULT_REGION", DEFAULT_REGION)
        .withEnv("AWS_DEFAULT_REGION", DEFAULT_REGION)
        .withEnv("AWS_ACCESS_KEY_ID", DEFAULT_ACCESS_KEY)
        .withEnv("AWS_SECRET_ACCESS_KEY", DEFAULT_SECRET_KEY)
        .withReuse(false);
  }

  /**
   * Creates a LocalStack container with persistence enabled
   *
   * @return Configured LocalStack container with persistence
   */
  public static LocalStackContainer createWithPersistence() {
    return create().withEnv("PERSISTENCE", "1").withEnv("SNAPSHOT_SAVE_STRATEGY", "ON_SHUTDOWN");
  }

  private LocalStackTestContainer() {
    // Utility class
  }
}
