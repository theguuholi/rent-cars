#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

registry=${IMAGE_NAMESPACE:-docker.io/guuhworship}
api_image="${registry%/}/rent-cars-api:latest"
web_image="${registry%/}/rent-cars-web:latest"

# Finish both builds before publishing either tag.
docker build --tag "$api_image" apps/api
docker build --tag "$web_image" apps/web
docker push "$api_image"
docker push "$web_image"
