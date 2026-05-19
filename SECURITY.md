# Security Policy

## 敏感信息处理

请不要在公开仓库中提交以下内容：

- 大模型 API Key
- 数据库真实密码
- Redis 真实密码
- 生产环境域名、IP、内网地址
- 真实业务数据样本

推荐做法：

- 使用 `.env` 或 `application-local.yml` 保存本地私有配置
- 提交 `.env.example` 和 `application-local.example.yml` 作为模板

## 漏洞披露

如果你发现安全问题，请不要直接公开提交包含利用细节的 Issue。

建议至少描述：

- 影响范围
- 复现条件
- 风险等级
- 建议修复方向
