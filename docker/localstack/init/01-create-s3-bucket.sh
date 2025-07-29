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
{"reviewId": "rev_001", "providerId": "agoda", "hotelId": "hotel_123", "guestName": "John Doe", "rating": 4.5, "reviewText": "Great hotel with excellent service!", "reviewDate": "2024-01-15", "language": "en", "location": "New York", "verified": true}
{"reviewId": "rev_002", "providerId": "booking", "hotelId": "hotel_124", "guestName": "Jane Smith", "rating": 3.8, "reviewText": "Good location but room could be cleaner.", "reviewDate": "2024-01-14", "language": "en", "location": "London", "verified": true}
{"reviewId": "rev_003", "providerId": "expedia", "hotelId": "hotel_125", "guestName": "Mike Johnson", "rating": 5.0, "reviewText": "Perfect stay! Highly recommended.", "reviewDate": "2024-01-13", "language": "en", "location": "Paris", "verified": false}
SAMPLE

awslocal s3 cp /tmp/sample-reviews.jl s3://review-files/reviews/2024/01/15/reviews.jl

echo "S3 bucket setup completed!"
