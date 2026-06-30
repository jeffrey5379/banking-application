#!/usr/bin/env bash
# start-dev.sh — start both backend and frontend in development mode

set -e
ROOT=$(cd "$(dirname "$0")" && pwd)

echo "=== Starting Bank App Dev Environment ==="

# Start backend in background
echo "[1/2] Starting Spring Boot backend..."
cd "$ROOT/backend"
mvn spring-boot:run &
BACKEND_PID=$!

# Wait for backend to be ready
echo "      Waiting for backend on :8080..."
until curl -s http://localhost:8080/api/users > /dev/null 2>&1; do
  sleep 2
done
echo "      Backend ready ✓"

# Start frontend
echo "[2/2] Starting Angular frontend..."
cd "$ROOT/frontend"
npm install --silent
npm start &
FRONTEND_PID=$!

echo ""
echo "  Frontend  →  http://localhost:4200"
echo "  Backend   →  http://localhost:8080"
echo "  H2 Console→  http://localhost:8080/h2-console"
echo ""
echo "Press Ctrl+C to stop both servers."

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT TERM
wait
