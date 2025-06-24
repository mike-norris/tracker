# Database Setup Guide

This guide provides detailed instructions for setting up each supported database with the Spring Boot Tracer Library.

## Table of Contents

- [PostgreSQL Setup](#postgresql-setup)
- [MySQL Setup](#mysql-setup)
- [MariaDB Setup](#mariadb-setup)
- [MongoDB Setup](#mongodb-setup)
- [Performance Optimization](#performance-optimization)
- [Troubleshooting](#troubleshooting)

---

## PostgreSQL Setup

### 1. Installation

**Docker (Recommended for Development):**
```bash
docker run --name tracer-postgres \
  -e POSTGRES_DB=tracing \
  -e POSTGRES_USER=tracer \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  -d postgres:15
```

**Production Installation:**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install postgresql postgresql-contrib

# CentOS/RHEL
sudo yum install postgresql-server postgresql-contrib
sudo postgresql-setup initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql
```

### 2. Database and User Creation

```sql
-- Connect as postgres user
sudo -u postgres psql

-- Create database and user
CREATE DATABASE tracing;
CREATE USER tracer WITH ENCRYPTED PASSWORD 'password';
GRANT ALL PRIVILEGES ON DATABASE tracing TO tracer;

-- Enable UUID extension
\c tracing
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";

-- Grant schema permissions
GRANT ALL ON SCHEMA public TO tracer;
```

### 3. Configuration

**application.yml:**
```yaml
tracing:
  database:
    type: postgresql
    connection:
      url: jdbc:postgresql://localhost:5432/tracing
      username: tracer
      password: password
      pool:
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout-ms: 30000
        idle-timeout-ms: 600000
        max-lifetime-ms: 1800000
```

### 4. Production Optimization

**postgresql.conf:**
```ini
# Memory settings
shared_buffers = 256MB
work_mem = 4MB
maintenance_work_mem = 64MB
effective_cache_size = 1GB

# Write performance
wal_buffers = 16MB
checkpoint_completion_target = 0.9
max_wal_size = 1GB
min_wal_size = 80MB

# Connection settings
max_connections = 100

# Logging
log_statement = 'mod'
log_min_duration_statement = 1000
```

### 5. Partitioning Setup (Optional)

```sql
-- Enable partitioning in configuration
-- The library will automatically create monthly partitions
SELECT create_monthly_partitions(2);

-- View partition status
SELECT * FROM partition_info;
```

---

## MySQL Setup

### 1. Installation

**Docker:**
```bash
docker run --name tracer-mysql \
  -e MYSQL_DATABASE=tracing \
  -e MYSQL_USER=tracer \
  -e MYSQL_PASSWORD=password \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -p 3306:3306 \
  -d mysql:8.0
```

**Production Installation:**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install mysql-server

# CentOS/RHEL
sudo yum install mysql-server
sudo systemctl enable mysqld
sudo systemctl start mysqld
```

### 2. Database and User Creation

```sql
-- Connect as root
mysql -u root -p

-- Create database and user
CREATE DATABASE tracing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tracer'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON tracing.* TO 'tracer'@'%';
FLUSH PRIVILEGES;
```

### 3. Configuration

**application.yml:**
```yaml
tracing:
  database:
    type: mysql
    connection:
      url: jdbc:mysql://localhost:3306/tracing?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
      username: tracer
      password: password
      pool:
        maximum-pool-size: 15
        minimum-idle: 3
        connection-timeout-ms: 30000
```

### 4. Production Optimization

**my.cnf:**
```ini
[mysqld]
# InnoDB settings
innodb_buffer_pool_size = 256M
innodb_log_file_size = 64M
innodb_flush_log_at_trx_commit = 2
innodb_file_per_table = 1

# Query cache
query_cache_type = 1
query_cache_size = 32M

# Connection settings
max_connections = 100
connect_timeout = 30
wait_timeout = 600
```

---

## MariaDB Setup

### 1. Installation

**Docker:**
```bash
docker run --name tracer-mariadb \
  -e MYSQL_DATABASE=tracing \
  -e MYSQL_USER=tracer \
  -e MYSQL_PASSWORD=password \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -p 3306:3306 \
  -d mariadb:10.11
```

### 2. Database and User Creation

```sql
-- Similar to MySQL
CREATE DATABASE tracing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'tracer'@'%' IDENTIFIED BY 'password';
GRANT ALL PRIVILEGES ON tracing.* TO 'tracer'@'%';
FLUSH PRIVILEGES;
```

### 3. Configuration

**application.yml:**
```yaml
tracing:
  database:
    type: mariadb
    connection:
      url: jdbc:mariadb://localhost:3306/tracing
      username: tracer
      password: password
      pool:
        maximum-pool-size: 15
        minimum-idle: 3
```

---

## MongoDB Setup

### 1. Installation

**Docker:**
```bash
docker run --name tracer-mongodb \
  -e MONGO_INITDB_DATABASE=tracing \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=adminpassword \
  -p 27017:27017 \
  -d mongo:6.0
```

**Production Installation:**
```bash
# Ubuntu/Debian
wget -qO - https://www.mongodb.org/static/pgp/server-6.0.asc | sudo apt-key add -
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/6.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-6.0.list
sudo apt update
sudo apt install mongodb-org
```

### 2. Database and User Creation

```javascript
// Connect to MongoDB
mongosh

// Create database and user
use tracing
db.createUser({
  user: "tracer",
  pwd: "password",
  roles: [
    { role: "readWrite", db: "tracing" },
    { role: "dbAdmin", db: "tracing" }
  ]
})

// Create indexes (optional - library will create them automatically)
db.userActions.createIndex({ "traceId": 1 })
db.userActions.createIndex({ "userId": 1, "timestamp": -1 })
db.userActions.createIndex({ "timestamp": -1 })

db.jobExecutions.createIndex({ "traceId": 1 })
db.jobExecutions.createIndex({ "status": 1 })
db.jobExecutions.createIndex({ "queueName": 1, "status": 1 })
```

### 3. Configuration

**application.yml:**
```yaml
tracing:
  database:
    type: mongodb
    mongodb:
      uri: mongodb://tracer:password@localhost:27017/tracing
      database: tracing
      user-actions-collection: userActions
      job-executions-collection: jobExecutions
      max-pool-size: 100
      min-pool-size: 10
      max-wait-time-ms: 10000
      max-connection-idle-time-ms: 120000
```

### 4. Production Optimization

**mongod.conf:**
```yaml
storage:
  wiredTiger:
    engineConfig:
      cacheSizeGB: 1
    collectionConfig:
      blockCompressor: snappy

net:
  maxIncomingConnections: 200

operationProfiling:
  slowOpThresholdMs: 1000
  mode: slowOp
```

---

## Performance Optimization

### General Guidelines

1. **Connection Pooling:**
    - Set appropriate pool sizes based on application load
    - Monitor connection usage and adjust accordingly
    - Use connection validation to prevent stale connections

2. **Indexing:**
    - The library creates optimal indexes automatically
    - Monitor query performance and add custom indexes if needed
    - Regularly analyze index usage and remove unused indexes

3. **Batch Processing:**
    - Tune batch sizes based on available memory and network capacity
    - Monitor batch processing metrics
    - Adjust async thread pool sizes based on load

### Database-Specific Optimizations

**PostgreSQL:**
- Enable partitioning for large datasets (>1M records/month)
- Use JSONB for action_data and input_data columns
- Consider using connection pooling (PgBouncer) for high-concurrency applications

**MySQL/MariaDB:**
- Use InnoDB storage engine (default)
- Configure appropriate buffer pool sizes
- Enable query cache for read-heavy workloads

**MongoDB:**
- Use appropriate read/write concerns
- Enable sharding for very large datasets
- Use compound indexes for complex queries

### Monitoring Queries

**PostgreSQL:**
```sql
-- Monitor slow queries
SELECT query, mean_time, calls, total_time 
FROM pg_stat_statements 
WHERE query LIKE '%user_actions%' OR query LIKE '%job_executions%'
ORDER BY mean_time DESC;

-- Check table sizes
SELECT 
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables 
WHERE tablename LIKE '%user_actions%' OR tablename LIKE '%job_executions%';
```

**MySQL:**
```sql
-- Monitor slow queries
SELECT 
  digest_text,
  count_star,
  avg_timer_wait/1000000000 as avg_time_sec
FROM performance_schema.events_statements_summary_by_digest 
WHERE digest_text LIKE '%user_actions%' OR digest_text LIKE '%job_executions%'
ORDER BY avg_timer_wait DESC;
```

**MongoDB:**
```javascript
// Monitor slow operations
db.setProfilingLevel(2, { slowms: 1000 })
db.system.profile.find().sort({ ts: -1 }).limit(5)

// Check collection stats
db.userActions.stats()
db.jobExecutions.stats()
```

---

## Troubleshooting

### Common Issues

#### 1. Connection Failures

**Symptoms:**
- Application fails to start
- Connection timeout errors
- Authentication failures

**Solutions:**
```yaml
# Increase connection timeout
tracing:
  database:
    connection:
      pool:
        connection-timeout-ms: 60000  # Increase from 30s to 60s

# Verify credentials and URL
# Check database server status
# Verify network connectivity
```

#### 2. Table Creation Failures

**Symptoms:**
- Tables not created automatically
- Permission denied errors

**Solutions:**
```yaml
# Ensure auto-creation is enabled
tracing:
  auto-create-tables: true

# Verify user permissions
# Check database logs for detailed errors
# Manually create tables if needed
```

#### 3. Performance Issues

**Symptoms:**
- Slow application response
- High memory usage
- Database connection pool exhaustion

**Solutions:**
```yaml
# Tune batch processing
tracing:
  database:
    batch-size: 500  # Reduce if memory issues
  async:
    core-pool-size: 8
    max-pool-size: 16
    queue-capacity: 1000

# Monitor and adjust connection pool
# Enable async processing
# Consider database optimization
```

#### 4. Data Inconsistency

**Symptoms:**
- Missing trace data
- Orphaned records
- Duplicate entries

**Solutions:**
- Verify transaction configuration
- Check for application errors during tracing
- Review batch processing logs
- Implement data validation queries

### Health Check Commands

**PostgreSQL:**
```sql
-- Check connection count
SELECT count(*) FROM pg_stat_activity WHERE datname = 'tracing';

-- Check table health
SELECT schemaname, tablename, n_live_tup, n_dead_tup 
FROM pg_stat_user_tables 
WHERE tablename IN ('user_actions', 'job_executions');
```

**MySQL/MariaDB:**
```sql
-- Check connection count
SHOW STATUS LIKE 'Threads_connected';

-- Check table health
SELECT TABLE_NAME, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'tracing';
```

**MongoDB:**
```javascript
// Check database status
db.runCommand({ serverStatus: 1 })

// Check collection health
db.runCommand({ collStats: "userActions" })
db.runCommand({ collStats: "jobExecutions" })
```

### Getting Help

If you encounter issues not covered in this guide:

1. **Check the application logs** for detailed error messages
2. **Review the health endpoints** at `/actuator/health/tracing`
3. **Search existing issues** on GitHub
4. **Create a new issue** with full error details and configuration

For performance issues, include:
- Database version and configuration
- Application load characteristics
- Resource usage metrics
- Query performance statistics