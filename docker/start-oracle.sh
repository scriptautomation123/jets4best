#!/bin/bash

echo "Starting Oracle Database container..."
docker-compose up -d oracle

echo "Waiting for Oracle to be ready..."
while ! docker exec oracle-db sqlplus -L sys/oracle123@//localhost:1521/XE as sysdba <<< "exit" >/dev/null 2>&1; do
  echo "Oracle is starting up... please wait"
  sleep 30
done

echo "Oracle Database is ready!"
echo "Connection details:"
echo "  Host: localhost"
echo "  Port: 1521"
echo "  Service: XE"
echo "  Username: system"
echo "  Password: oracle123"
echo "  SYS Password: oracle123"
echo ""
echo "Enterprise Manager: http://localhost:5500/em" 