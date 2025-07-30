# Create directory structure for Docker configurations
mkdir -p docker/postgres/init
mkdir -p docker/localstack/init
mkdir -p docker/prometheus
mkdir -p logs
mkdir -p data

# PostgreSQL initialization script
cat > docker/postgres/init/01-init-db.sql << 'EOF'
-- Create additional schemas if needed
CREATE SCHEMA IF NOT EXISTS reviews;
CREATE SCHEMA IF NOT EXISTS audit;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Set timezone
SET timezone = 'UTC';

-- Create initial tables (will be managed by Flyway migrations)
-- This is just for initial setup, actual schema will be in migrations

GRANT ALL PRIVILEGES ON DATABASE reviewsystem TO reviewuser;
GRANT ALL PRIVILEGES ON SCHEMA reviews TO reviewuser;
GRANT ALL PRIVILEGES ON SCHEMA audit TO reviewuser;
EOF

# LocalStack S3 initialization script
cat > docker/localstack/init/01-create-s3-bucket.sh << 'EOF'
#!/bin/bash
echo "Creating S3 bucket for review files..."

# Wait for LocalStack to be ready
sleep 5

# Create the S3 bucket
awslocal s3 mb s3://review-files

# Set bucket policy to allow access
awslocal s3api put-bucket-policy --bucket review-files --policy '{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": [
        "arn:aws:s3:::review-files",
        "arn:aws:s3:::review-files/*"
      ]
    }
  ]
}'

# Upload sample review files for testing
cat > /tmp/sample-reviews.jl << 'SAMPLE'
{"hotelId": 10984, "platform": "Agoda", "hotelName": "Oscar Saigon Hotel", "comment": {"isShowReviewResponse": false, "hotelReviewId": 948353737, "providerId": 332, "rating": 6.4, "checkInDateMonthAndYear": "April 2025", "encryptedReviewData": "cZwJ6a6ZoFX2W5WwVXaJkA==", "formattedRating": "6.4", "formattedReviewDate": "April 10, 2025", "ratingText": "Good", "responderName": "Oscar Saigon Hotel", "responseDateText": "", "responseTranslateSource": "en", "reviewComments": "Hotel room is basic and very small. not much like pictures. few areas were getting repaired. but since location is so accessible from all main areas in district-1, i would prefer to stay here again. Staff was good.", "reviewNegatives": "", "reviewPositives": "", "reviewProviderLogo": "", "reviewProviderText": "Agoda", "reviewTitle": "Perfect location and safe but hotel under renovation ", "translateSource": "en", "translateTarget": "en", "reviewDate": "2025-04-10T05:37:00+07:00", "reviewerInfo": {"countryName": "India", "displayMemberName": "********", "flagName": "in", "reviewGroupName": "Solo traveler", "roomTypeName": "Premium Deluxe Double Room", "countryId": 35, "lengthOfStay": 2, "reviewGroupId": 3, "roomTypeId": 0, "reviewerReviewedCount": 0, "isExpertReviewer": false, "isShowGlobalIcon": false, "isShowReviewedCount": false}, "originalTitle": "", "originalComment": "", "formattedResponseDate": ""}, "overallByProviders": [{"providerId": 332, "provider": "Agoda", "overallScore": 7.9, "reviewCount": 7070, "grades": {"Cleanliness": 7.7, "Facilities": 7.2, "Location": 9.1, "Room comfort and quality": 7.5, "Service": 7.8, "Value for money": 7.8}}]}
{"hotelId": 1196256, "platform": "Agoda", "hotelName": "Thai Modern Resort & Spa - Newly Renovated", "comment": {"isShowReviewResponse": false, "hotelReviewId": 947286175, "providerId": 332, "rating": 9.2, "checkInDateMonthAndYear": "March 2025", "encryptedReviewData": "5ju6BkwlTWeL58bXFZwSSg==", "formattedRating": "9.2", "formattedReviewDate": "April 09, 2025", "ratingText": "Exceptional", "responderName": "Thai Modern Resort & Spa - Newly Renovated", "responseDateText": "", "responseTranslateSource": "de", "reviewComments": "Kleine Oase mit nettem Personal  ... gerne wieder.", "reviewNegatives": "", "reviewPositives": "", "reviewProviderLogo": "", "reviewProviderText": "Agoda", "reviewTitle": "Kleine Oase mit nettem Personal", "translateSource": "de", "translateTarget": "en", "reviewDate": "2025-04-09T09:37:00+07:00", "reviewerInfo": {"countryName": "Germany", "displayMemberName": "******", "flagName": "de", "reviewGroupName": "Solo traveler", "roomTypeName": "Garden View Villa", "countryId": 101, "lengthOfStay": 10, "reviewGroupId": 3, "roomTypeId": 0, "reviewerReviewedCount": 16, "isExpertReviewer": false, "isShowGlobalIcon": false, "isShowReviewedCount": false}, "originalTitle": "", "originalComment": "", "formattedResponseDate": ""}, "overallByProviders": [{"providerId": 332, "provider": "Agoda", "overallScore": 8.4, "reviewCount": 11, "grades": {"Cleanliness": 8.4, "Facilities": 8.4, "Location": 7.3, "Service": 8.4, "Value for money": 8.6}}]}
SAMPLE

awslocal s3 cp /tmp/sample-reviews.jl s3://review-files/reviews/2024/01/15/reviews.jl

echo "S3 bucket setup completed!"
EOF

chmod +x docker/localstack/init/01-create-s3-bucket.sh

# Prometheus configuration
cat > docker/prometheus/prometheus.yml << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  # - "first_rules.yml"
  # - "second_rules.yml"

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'review-system-app'
    static_configs:
      - targets: ['review-system-app:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
EOF

# Environment file for development
cat > .env.local << 'EOF'
# Database Configuration
POSTGRES_DB=reviewsystem
POSTGRES_USER=reviewuser
POSTGRES_PASSWORD=reviewpass

# AWS Configuration (LocalStack)
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
AWS_ENDPOINT_URL=http://localhost:4566
AWS_S3_BUCKET_NAME=review-files

# Application Configuration
SPRING_PROFILES_ACTIVE=local
LOGGING_LEVEL_ROOT=INFO
REVIEW_PROCESSOR_ENABLED=true

# Ports
POSTGRES_PORT=5432
LOCALSTACK_PORT=4566
APP_PORT=8080
REDIS_PORT=6379
PROMETHEUS_PORT=9090
EOF

# Docker Compose override for development
cat > docker-compose.override.yml << 'EOF'

services:
  review-system-app:
    volumes:
      # Mount source code for hot reload during development
      - ./src:/app/src:ro
      - ./target:/app/target
    environment:
      - SPRING_DEVTOOLS_RESTART_ENABLED=true
      - SPRING_DEVTOOLS_LIVERELOAD_ENABLED=true
    ports:
      - "35729:35729"  # LiveReload port

  postgres:
    # Expose additional debugging port
    environment:
      - POSTGRES_LOG_STATEMENT=all
      - POSTGRES_LOG_MIN_DURATION_STATEMENT=0

  localstack:
    # Enable additional AWS services for future use
    environment:
      - SERVICES=s3,sqs,sns,lambda
      - DEBUG=1
      - LS_LOG=trace
EOF

# Makefile for easy development commands
cat > Makefile << 'EOF'
.PHONY: up down build logs clean test db-reset s3-setup

# Start all services
up:
	docker-compose up -d

# Stop all services
down:
	docker-compose down

# Build and start services
build:
	docker-compose up -d --build

# View logs
logs:
	docker-compose logs -f

# View logs for specific service
logs-%:
	docker-compose logs -f $*

# Clean up everything
clean:
	docker-compose down -v
	docker system prune -f

# Run tests
test:
	docker-compose exec review-system-app ./mvnw test

# Reset database
db-reset:
	docker-compose stop postgres
	docker volume rm review-system-microservice_postgres_data
	docker-compose up -d postgres

# Setup S3 bucket manually
s3-setup:
	docker-compose exec localstack bash -c "awslocal s3 mb s3://review-files"

# Health check all services
health:
	@echo "Checking service health..."
	@docker-compose ps
	@echo "\nPostgreSQL:"
	@docker-compose exec postgres pg_isready -U reviewuser -d reviewsystem
	@echo "\nLocalStack:"
	@curl -s http://localhost:4566/health || echo "LocalStack not ready"
	@echo "\nApplication:"
	@curl -s http://localhost:8080/actuator/health || echo "Application not ready"

# View S3 bucket contents
s3-list:
	docker-compose exec localstack awslocal s3 ls s3://review-files --recursive

# Tail application logs
app-logs:
	docker-compose logs -f review-system-app

# Database shell
db-shell:
	docker-compose exec postgres psql -U reviewuser -d reviewsystem
EOF

echo "Docker development environment setup completed!"
echo ""
echo "Directory structure created:"
echo "├── docker/"
echo "│   ├── postgres/init/01-init-db.sql"
echo "│   ├── localstack/init/01-create-s3-bucket.sh"
echo "│   └── prometheus/prometheus.yml"
echo "├── logs/"
echo "├── data/"
echo "├── .env.local"
echo "├── docker-compose.override.yml"
echo "└── Makefile"
echo ""
echo "To start the development environment:"
echo "1. make up"
echo "2. make health (to check all services)"
echo "3. make s3-list (to verify S3 setup)"