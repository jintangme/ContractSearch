# 部署指南

## 架构说明

```
Nacos 配置中心 (localhost:8848)
  ↓ 配置监听
Python Service (scse-discover)
  ↓ 定时任务
  ↓ 生成 parquet 文件
  ↓ HTTP 回调
Spring Service (cloud-service:18080)
  ↓ 处理合约数据
```

## 部署步骤

### 1. 启动 Nacos

确保 Nacos 已启动并运行在 `localhost:8848`

### 2. 配置 Nacos

登录 Nacos 控制台 (http://localhost:18080/index.html#)，创建配置：
- Data ID: `scse-discover.yaml`
- Group: `DEFAULT_GROUP`
- 配置内容参考 `NACOS_CONFIG.md`

### 3. 配置 Python 服务

```bash
cd scse-discover
cp .env.example .env
# 编辑 .env 填入实际的 API keys
```

### 4. 安装依赖

```bash
pip install -r requirements.txt
```

### 5. 启动服务

```bash
# Python 服务
uvicorn app.main:app --host 0.0.0.0 --port 18000

# Spring 服务
cd cloud-service
mvn spring-boot:run
```

## 验证

1. 检查 Python 服务健康状态：
   ```bash
   curl http://localhost:18000/health
   ```

2. 查看日志确认配置已加载和定时任务已启动

3. 在 Nacos 控制台修改配置，观察服务日志确认配置已更新
