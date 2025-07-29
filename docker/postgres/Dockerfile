# Use the official Postgres image as the base
FROM postgres:15-alpine

# Copy your init scripts into the expected directory inside the container
# The `COPY` command typically retains some permissions, but ensuring them is good practice.
# For .sql files, execute permission isn't strictly necessary, but it doesn't hurt.
COPY ./docker/postgres/init /docker-entrypoint-initdb.d/

# Ensure the init scripts are executable (especially for .sh files)
RUN chmod -R +x /docker-entrypoint-initdb.d/
