# Contributing

欢迎提交 Issue 和 Pull Request。

## 提交前建议

1. 确认本地配置文件未被提交：
   - `data-service/src/main/resources/application-local.yml`
   - `.env`
2. 确认依赖缓存和构建产物未进入暂存区：
   - `venv/`
   - `node_modules/`
   - `target/`
   - `.m2/`
3. 运行基础校验：

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
mvn -q -s .mvn/settings-central.xml -pl data-service -Dmaven.repo.local=.m2/repository test
```

```bash
npm run build
```

## Commit 规范

建议使用 Conventional Commits 风格，例如：

- `feat: add expert annotation pagination`
- `fix: prevent duplicate queue recovery`
- `docs: improve setup guide`

## Pull Request 建议说明

- 改动目标
- 核心实现
- 风险点
- 测试方式
