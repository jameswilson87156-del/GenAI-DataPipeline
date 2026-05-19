# GenAI DataPipeline

Spring Boot 3.x backend for importing raw text, cleaning it asynchronously, and tracking task, item, and worker-node state.

## Start Infrastructure

```bash
docker compose up -d
```

Services:

- MySQL: `localhost:13306`, database `genai_data_pipeline`, user `root`, password `123456`
- Redis: `localhost:16379`, password `123456`

The MySQL schema is initialized from `docker/mysql/init/01-schema.sql` when the MySQL volume is created for the first time.

## Run Data Service

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -pl data-service spring-boot:run
```

Base URL:

```text
http://localhost:8081
```

Health check:

```bash
curl -s http://localhost:8081/api/health
```

## Run Everything With Docker

```bash
docker compose --profile app up -d --build
```

## End-to-End Flow

1. Create a data task:

```bash
curl -s -X POST http://localhost:8081/api/data-tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "taskName": "demo-clean-task",
    "taskType": "clean",
    "sourceType": "json",
    "sourceUri": "manual://demo",
    "remark": "Demo import and clean task"
  }'
```

2. Import raw text items and auto-start cleaning:

```bash
curl -s -X POST http://localhost:8081/api/data-items/import \
  -H 'Content-Type: application/json' \
  -d '{
    "taskId": 1,
    "rawContents": [
      "<p>Hello&nbsp;world</p>",
      "Clean this text too"
    ],
    "sourcePrefix": "demo",
    "autoStart": true
  }'
```

3. Query task progress:

```bash
curl -s 'http://localhost:8081/api/data-tasks/1'
```

4. Query cleaned items:

```bash
curl -s 'http://localhost:8081/api/data-items?taskId=1&current=1&size=10'
```

You can also start a cleaning pipeline for an existing task with pending items:

```bash
curl -s -X POST http://localhost:8081/api/data-tasks/1/start-clean
```

The legacy route remains available:

```bash
curl -s -X POST http://localhost:8081/api/task/1/start
```

## Main APIs

- `GET /api/health`: service health check.
- `POST /api/data-tasks`: create a task.
- `GET /api/data-tasks`: page tasks by `status` or `taskType`.
- `GET /api/data-tasks/{id}`: get task detail.
- `PUT /api/data-tasks/{id}`: update task metadata and counters.
- `POST /api/data-tasks/{id}/start`: mark task as running.
- `POST /api/data-tasks/{id}/stop`: mark task as stopped.
- `POST /api/data-tasks/{id}/start-clean`: publish pending items to Redis and clean asynchronously.
- `DELETE /api/data-tasks/{id}`: logically delete a task.
- `POST /api/data-items`: create one item.
- `POST /api/data-items/import`: import JSON raw contents.
- `POST /api/data-items/import-file`: import one UTF-8 text file, one item per line.
- `GET /api/data-items`: page items by `taskId` or `status`.
- `GET /api/data-items/{id}`: get item detail.
- `PUT /api/data-items/{id}`: update item.
- `DELETE /api/data-items/{id}`: logically delete an item.
- `POST /api/worker-nodes`: create a worker node.
- `GET /api/worker-nodes`: page worker nodes by `status`.
- `POST /api/worker-nodes/{id}/heartbeat`: mark worker node online and update heartbeat time.

OpenAPI UI is available at:

```text
http://localhost:8081/swagger-ui/index.html
```

## Status Codes

Task status:

- `0`: created
- `1`: running
- `2`: paused
- `3`: completed
- `4`: failed
- `5`: stopped

Data item status:

- `0`: pending
- `1`: processing
- `2`: cleaned
- `3`: failed
- `4`: skipped

## Reliable Queue

The clean pipeline uses Redis reliable queues plus MySQL CAS:

- Pending queue: `pipeline:queue:pending:{taskId}`
- Processing queue: `pipeline:queue:processing:{taskId}`
- Item lock: `pipeline:lock:item:{itemId}`

Publishing scans MySQL `data_item` rows whose `status = 0` in ID batches and pushes their IDs into the pending queue.

Consumers use Redis `BRPOPLPUSH` semantics through Spring Data Redis `rightPopAndLeftPush(source, destination, timeout, unit)`: one item ID is atomically moved from pending to processing before Java starts cleaning. If the Java process crashes after this move, the ID remains in processing.

The actual write is protected by MySQL CAS:

```sql
UPDATE data_item
SET cleaned_content = ?, status = 2, cleaned_at = ?, update_time = ?
WHERE id = ? AND status = 0;
```

`rows > 0` means this worker won the item and writes the cleaned result, then ACKs with `LREM processing 1 itemId`. `rows = 0` means another worker already changed the row, so this worker only ACKs and skips it.

A scheduled recovery job runs every 5 minutes. It scans `pipeline:queue:processing:*` with Redis `SCAN` and moves residual IDs back to the matching pending queue with `RPOPLPUSH`.

Worker status:

- `0`: offline
- `1`: online
- `2`: busy
- `3`: disabled

## Test

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -pl data-service test
```
