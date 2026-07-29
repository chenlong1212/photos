# PeachWuhu

PeachWuhu 是 Vue 3 + TypeScript + Spring Boot + MySQL 版本的私人相册。前端样式与交互由原 Flask/Jinja 页面逐页迁移，生产照片继续存放在服务器文件系统。

## 目录

```text
frontend/       Vue 3、TypeScript、Vite
backend/        Spring Boot、JDBC、Flyway
migration/      SQLite 到 MySQL 的一次性迁移工具
deploy/         Nginx 与 systemd 配置
photos/         本地开发图片（不随生产发布）
compose.yaml    本地 MySQL
```

## 本地 dev

首次运行先复制配置模板，并填写本机账号密码：

```bash
cp backend/src/main/resources/application-dev.yaml.example \
   backend/src/main/resources/application-dev.yaml
```

```bash
# 1. 启动 MySQL
docker compose up -d mysql

# 2. 首次启动后端，让 Flyway 建表
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 另开终端，导入本地实际存在的图片数据
python3 -m pip install -r migration/requirements.txt
python3 migration/sqlite_to_mysql.py \
  --existing-files-only \
  --password YOUR_DEV_DB_PASSWORD

# 4. 启动前端
cd frontend
npm install
npm run dev
```

访问 `http://localhost:8081/`。默认开发登录信息在 `application-dev.yaml`。

本地端口：

- Vue：`8081`
- Spring Boot：`18000`
- MySQL：`3307`

## 构建

```bash
cd backend
mvn clean package

cd ../frontend
npm run build
```

构建产物：

```text
backend/target/peachwuhu.jar
frontend/dist/
```

## 生产

生产部署前复制配置模板并填写服务器账号密码：

```bash
cp backend/src/main/resources/application-prod.yaml.example \
   backend/src/main/resources/application-prod.yaml
```

真实的 `application-dev.yaml` 和 `application-prod.yaml` 已被 Git 忽略，不会上传到仓库。

当前部署结构：

```text
/opt/peachwuhu/
├── releases/
│   └── 20260729-220900/
│       ├── peachwuhu.jar
│       ├── dist/
│       ├── peachwuhu.service
│       └── peachwuhu.conf
├── mysql8-data/        MySQL 8.4 持久化数据
├── backups/            数据库迁移备份
└── current -> releases/20260729-220900

/peachwuhu/photos/        生产照片，不随版本发布替换
```

生产访问地址：`http://47.95.227.6/peachwuhu/`

服务命令：

```bash
systemctl status peachwuhu
systemctl restart peachwuhu
journalctl -u peachwuhu -f
```

## 数据规则

- MySQL 只保存相对照片路径。
- 原图和缩略图始终位于配置的 `peachwuhu.storage-root` 下。
- Flyway 只管理数据库结构。
- `migration/sqlite_to_mysql.py` 负责历史 SQLite 业务数据迁移。
- 本地使用 `--existing-files-only`，只导入原图和缩略图都存在的记录。
