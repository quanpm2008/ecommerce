#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

for service in *-service; do
  docker build --network=host -t "ecommerce-$service" "$service"
done

docker compose -p ecommerce up -d --no-build
