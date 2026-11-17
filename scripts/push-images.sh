#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

registry=${IMAGE_NAMESPACE:-docker.io/guuhworship}
api_image="${registry%/}/rent-cars-api:latest"
web_image="${registry%/}/rent-cars-web:latest"
recommendations_image="${registry%/}/rent-cars-recommendations:latest"

# Finish all three builds before publishing any tag.
docker build --tag "$api_image" apps/api
docker build --tag "$web_image" apps/web
docker build --tag "$recommendations_image" apps/recommendations
docker push "$api_image"
docker push "$web_image"
docker push "$recommendations_image"
