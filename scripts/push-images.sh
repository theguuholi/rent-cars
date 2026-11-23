#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

registry=${IMAGE_NAMESPACE:-docker.io/guuhworship}
api_image="${registry%/}/rent-cars-api:latest"
web_image="${registry%/}/rent-cars-web:latest"
recommendations_image="${registry%/}/rent-cars-recommendations:latest"

job_image="${registry%/}/rent-cars-recommendations-job:latest"

# Finish all four builds before publishing any tag.
docker build --tag "$api_image" apps/api
docker build --tag "$web_image" apps/web
docker build --tag "$recommendations_image" apps/recommendations
docker build --tag "$job_image" apps/recommendations-job
docker push "$api_image"
docker push "$web_image"
docker push "$recommendations_image"
docker push "$job_image"
