# Use the official LocalStack image as the base
FROM localstack/localstack:3.0

# Copy your init scripts into the expected directory inside the container
# The `COPY` command handles permissions reasonably well by default,
# but adding a chmod ensures execute permissions for shell scripts.
COPY ./docker/localstack/init /etc/localstack/init/ready.d/

# Ensure the init scripts are executable (especially for .sh files)
RUN chmod -R +x /etc/localstack/init/ready.d/
