#!/usr/bin/env bash
# Starts the whole platform locally on the H2 profile (no MySQL/Redis/Docker needed).
# Usage: scripts/run-local.sh [start|stop|status]      Logs: ./logs/<service>.log
# Requirements: JDK 17 on PATH (or JAVA_HOME), jars built with:  ./mvnw -q -DskipTests package
set -u
cd "$(dirname "$0")/.."
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
export JWT_SECRET="${JWT_SECRET:-local-dev-secret-please-change-me-0123456789}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin12345}"
export GATEWAY_PORT="${GATEWAY_PORT:-8080}"
mkdir -p logs
PLATFORM="eureka-server:8761 config-server:8888"
SERVICES="auth-service:8081 customer-service:8082 product-service:8083 quote-service:8084 proposal-service:8085 payment-service:8086 policy-service:8087 notification-service:8088 document-service:8089 claims-service:8090"

wait_healthy() {   # name url
  for i in $(seq 1 90); do
    if curl -sf "$2" >/dev/null 2>&1; then echo "  $1 is up"; return 0; fi
    sleep 2
  done
  echo "  $1 did not become healthy in time (see logs/$1.log)"; return 1
}
start_one() {      # name port [extra env...]
  local name=$1 port=$2; shift 2
  env SERVER_PORT="$port" "$@" nohup "$JAVA" -jar "$name/target/$name-1.0.0-SNAPSHOT.jar" > "logs/$name.log" 2>&1 &
}
case "${1:-start}" in
  start)
    for e in $PLATFORM; do start_one "${e%%:*}" "${e#*:}"; done
    wait_healthy eureka-server http://localhost:8761/actuator/health
    wait_healthy config-server http://localhost:8888/actuator/health
    start_one api-gateway "$GATEWAY_PORT"
    for e in $SERVICES; do start_one "${e%%:*}" "${e#*:}" SPRING_PROFILES_ACTIVE=h2; done
    wait_healthy api-gateway "http://localhost:$GATEWAY_PORT/actuator/health"
    for e in $SERVICES; do wait_healthy "${e%%:*}" "http://localhost:${e#*:}/actuator/health"; done
    echo "Platform ready: gateway http://localhost:$GATEWAY_PORT  eureka http://localhost:8761  admin: admin@insurance.local / $ADMIN_PASSWORD"
    ;;
  stop)
    for e in api-gateway:$GATEWAY_PORT $PLATFORM $SERVICES; do
      port=${e#*:}
      if command -v taskkill >/dev/null 2>&1; then
        for pid in $(netstat -ano | grep -E ":$port .*LISTENING" | awk '{print $NF}' | sort -u); do taskkill //PID "$pid" //F >/dev/null 2>&1; done
      else
        lsof -ti tcp:"$port" | xargs -r kill
      fi
    done
    echo "stopped"
    ;;
  status)
    for e in api-gateway:$GATEWAY_PORT $PLATFORM $SERVICES; do
      printf "%-22s " "${e%%:*}"; curl -sf "http://localhost:${e#*:}/actuator/health" >/dev/null && echo UP || echo DOWN
    done
    ;;
esac
