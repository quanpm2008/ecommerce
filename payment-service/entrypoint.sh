#!/usr/bin/env bash
set -euo pipefail

java -jar /app/simulator/target/payment-simulator.jar &
SIM_PID=$!

cleanup() {
  kill "$SIM_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

mvn -B spring-boot:run
