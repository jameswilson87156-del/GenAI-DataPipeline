# GenAI-DataPipeline

一个面向 AIGC 数据资产生产场景的全栈项目，围绕“异构清洗 + 可靠队列 + 大模型预标注 + 专家复核”构建完整的人机协同闭环。

> 一个将 **分布式数据处理、异构清洗节点、AI 预标注与专家终审工作台** 串成完整生产闭环的工程化项目。

![Java](https://img.shields.io/badge/Java-17-1f6feb)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-3fb950)
![Vue](https://img.shields.io/badge/Vue-3-42b883)
![Python](https://img.shields.io/badge/Python-FastAPI-3776ab)
![License](https://img.shields.io/badge/License-MIT-blue)

项目当前已经打通以下主链路：

- Java `Spring Boot 3` 主控服务，负责任务调度、状态流转、可靠消费与数据落库
- Python `FastAPI` 清洗节点，负责文本清洗与代码预处理
- `Redis Reliable Queue + MySQL CAS` 高并发防漏队列
- `LangChain4j + OpenAI Compatible API` AI 辅助预标注
- `Vue 3 + Vite + Element Plus` 专家工作台，支持流水线式标注与快捷键操作

---

## 快速开始

如果你只想最快把项目跑起来，按下面顺序执行即可：

```bash
cp .env.example .env
cp data-service/src/main/resources/application-local.example.yml data-service/src/main/resources/application-local.yml
docker compose up -d mysql redis
```

然后分别启动：

```bash
source venv/bin/activate
uvicorn clean_worker:app --host 0.0.0.0 --port 8000
```

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
mvn -q -s .mvn/settings-central.xml -pl data-service -Dmaven.repo.local=.m2/repository spring-boot:run
```

```bash
npm install
npm run dev
```

前端访问：

```text
http://localhost:5173/annotation
```

---

## 1. 项目亮点

### 异构算力协同

清洗任务由 Java 主控服务消费，但真正的内容清洗派发给 Python 节点执行：

- `TEXT`：去 HTML 标签、压缩空白、清理乱码控制符
- `CODE`：去冗余空行、基础格式归一化、为后续 AST / 语义处理做预清洗

这种设计把“调度”和“内容处理”解耦，便于后续水平扩展多种 worker。

### 可靠队列与防漏消费

采用：

- Redis `pending / processing` 双队列
- `BRPOPLPUSH` 原子迁移
- MySQL `CAS` 乐观锁更新
- `processing` 超时补偿恢复
- 重试次数控制与失败落库

可以有效避免：

- 多消费者重复写入
- 消费中途宕机导致数据丢失
- 某个 item 无限重试卡死任务

### AI 辅助半自动标注

代码类数据在清洗完成后，会异步进入 AI 预标注阶段：

- 调用 `LangChain4j` 接入的大模型
- 生成结构化 JSON 标注结果
- 写入 `ai_annotation`
- 将数据推进到 `待专家标注`

### Human-in-the-Loop 专家复核

前端工作台支持：

- 查看清洗结果
- 查看 AI 推理链与预标注
- 一键采纳 AI 结果
- 手工修正后提交
- 自动拉取下一条待标注数据

最终形成“AI 辅助 + 人工确认”的高质量数据资产闭环。

---

## 2. 技术栈

### 后端

- Java 17
- Spring Boot 3.5.x
- Spring Cloud OpenFeign
- Resilience4j
- MyBatis-Plus
- Redisson
- MySQL 8
- Redis 7
- LangChain4j

### Python

- FastAPI
- Uvicorn

### 前端

- Vue 3
- TypeScript
- Vite
- Vue Router
- Axios
- Element Plus

---

## 3. 系统架构

```text
前端工作台(Vue3)
        |
        v
Java 主控服务(Spring Boot 8081)
        |
        | 1. 任务发布 / Redis可靠消费
        | 2. 调 Python 清洗节点
        | 3. MySQL CAS 落库
        | 4. 调用大模型预标注
        v
Python 清洗节点(FastAPI 8000/8005)

        + Redis(16379)
        + MySQL(13306)
```

---

## 4. 当前核心流程

### 阶段一：数据导入

- 创建 `data_task`
- 批量导入 `data_item`
- 自动识别 `data_type`

### 阶段二：异构清洗

- Java 从 Redis `pending` 拉取 item
- 原子迁移到 `processing`
- 调 Python `/api/v1/clean`
- MySQL CAS 更新 `cleaned_content`

### 阶段三：AI 预标注

- 对 `CODE` 类型数据调用大模型
- 返回结构化 JSON：

```json
{
  "is_bug": true,
  "bug_type": "NullPointer",
  "suggestion": "add null check"
}
```

- 结果落入 `ai_annotation`

### 阶段四：专家终审

- 前端获取 `status = 2` 的待标注数据
- 显示 `cleaned_content + ai_annotation`
- 专家一键采纳或手工修改
- 提交 `expert_annotation`
- 数据状态推进为 `3`

---

## 5. 状态机设计

### 任务状态 `data_task.status`

- `0`：已创建
- `1`：运行中
- `2`：已暂停
- `3`：已完成
- `4`：失败
- `5`：已停止

### 数据项状态 `data_item.status`

- `0`：待处理
- `1`：处理中
- `2`：待专家标注
- `3`：已完成
- `4`：失败
- `5`：跳过

---

## 6. 目录结构

```text
genai-parent
├── clean_worker.py                    # Python FastAPI 清洗节点
├── data-service/                      # Java 主控服务
│   ├── src/main/java/...              # Controller / Service / Feign / LLM 逻辑
│   ├── src/main/resources/            # Spring 配置、数据库 schema
│   └── src/test/java/...              # 核心服务测试
├── docker/mysql/init/01-schema.sql    # 初始化建表脚本
├── src/                               # Vue 3 前端工程
│   ├── api/
│   ├── router/
│   ├── types/
│   └── views/
├── docker-compose.yml                 # MySQL / Redis / data-service 编排
└── README.md
```

---

## 7. 本地启动

### 7.1 启动基础设施

```bash
docker compose up -d mysql redis
```

服务说明：

- MySQL：`localhost:13306`
- Redis：`localhost:16379`

默认账号密码请以本地 `docker-compose.yml` 或环境变量为准。
如果项目需要公开展示，建议第一时间替换为你自己的本地开发口令，不要在公开场景中复用弱密码。

---

### 7.2 启动 Python 清洗节点

如果你使用本地虚拟环境：

```bash
cd ~/genai-parent
source venv/bin/activate
uvicorn clean_worker:app --host 0.0.0.0 --port 8000
```

---

### 7.3 启动 Java 主控服务

```bash
cd ~/genai-parent
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
mvn -q -s .mvn/settings-central.xml \
  -pl data-service \
  -Dmaven.repo.local=.m2/repository \
  spring-boot:run
```

启动成功后：

- 后端地址：`http://localhost:8081`
- Swagger：`http://localhost:8081/swagger-ui/index.html`
- 健康检查：`http://localhost:8081/api/health`

---

### 7.4 启动前端工作台

```bash
cd ~/genai-parent
npm install
npm run dev
```

打开浏览器：

```text
http://localhost:5173/annotation
```

前端开发代理已配置：

- 前端请求 `/dev-api/...`
- 自动代理到 `http://localhost:8081/...`

---

## 8. 常用接口

### 基础接口

- `GET /api/health`
- `POST /api/data-tasks`
- `GET /api/data-tasks/{id}`
- `GET /api/data-items`
- `POST /api/data-items/import`

### 清洗与任务流转

- `POST /api/data-tasks/{taskId}/start-clean`
- `POST /api/task/{taskId}/start`

### 专家标注接口

- `GET /api/v1/annotation/next?taskId=...`
- `POST /api/v1/annotation/submit`

---

## 9. 前端工作台能力

当前前端页面已支持：

- 输入任务 ID 加载待标注条目
- 深色 Cyberpunk 风格工作台
- Monaco 风格代码展示区
- AI 推理链折叠面板
- AI 结构化标签卡片
- 人工标注表单
- 快捷键：
  - `Alt + A`：采纳 AI 预标注
  - `Ctrl + Enter`：提交并下一条
- 骨架屏、加载态和防连击

---

## 10. 测试

后端测试：

```bash
cd ~/genai-parent
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
mvn -q -s .mvn/settings-central.xml \
  -pl data-service \
  -Dmaven.repo.local=.m2/repository \
  test
```

---

## 11. 安全说明

以下内容已加入 `.gitignore`，不会提交到远程仓库：

- `data-service/src/main/resources/application-local.yml`
- `venv/`
- `node_modules/`
- `target/`
- `.m2/`

请将大模型密钥只写入本地 `application-local.yml` 或环境变量中，不要提交到 GitHub。
如果你要把项目设为公开仓库，建议进一步检查以下内容是否需要本地化处理：

- 数据库连接密码
- Redis 密码
- 本地 IP / 主机名
- 真实任务 ID、业务样例数据

---

## 12. 项目适合展示的关键词

如果你要把这个项目写进简历或 GitHub 描述，推荐使用这些关键词：

- AIGC 数据资产生产系统
- Human-in-the-Loop 标注闭环
- Redis Reliable Queue
- MySQL CAS 乐观锁
- 异构 Python 清洗节点
- LangChain4j 代码预标注
- Vue 3 专家工作台
- 分布式任务恢复与状态机设计

---

## 13. 后续可扩展方向

- 多 Python Worker 注册与调度
- 专家登录态与权限系统
- 标注历史审计与回滚
- 批量任务 dashboard
- 统计报表与产能分析
- Docker 一键全链路部署

---

如果你觉得这个项目对你有帮助，欢迎 Star。
