#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

for service in *-service; do
  if [ "$service" = "payment-service" ]; then
    docker build --network=host -t "ecommerce-$service" -f "$service/Dockerfile" .
  else
    docker build --network=host -t "ecommerce-$service" "$service"
  fi
done

docker compose -p ecommerce up -d --no-build
