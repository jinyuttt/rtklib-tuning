# RTKLIB Tuning

RTKLIB 参数调优与跳变诊断工具，基于 Spring Boot + RTKLIB 构建，提供可视化 Web 界面进行 GNSS 定位参数调试、独立定位和单文件分析。

## 功能模块

### 🛰 参数调试

- 多参数组批量评估：配置多组 RTKLIB 参数，自动逐组解算并对比固定率
- SSE 实时进度推送：评估过程实时反馈当前参数组、固定率、耗时
- 轨迹图可视化：
  - N/E/U 三方向坐标曲线，中位数参考点
  - 连线 + 散点组合显示，Fixed/Float/Single 分色
  - 支持概览/详细模式切换，可设置显示天数和最大数据量
- 解状态时序图 + 坐标偏差图：评估完成后自动加载
- 基站/流动站配置管理

### 📡 独立定位

- 支持 Serial / TCP / NTRIP / File 多种数据源
- WebSocket 实时推送定位结果
- 实时轨迹显示与解状态监控

### 📂 单文件分析

- RTCM / RINEX / POS / SP3 文件分析
- 天空图、DOP 值、SNR 时序/分布图
- 缺历检测与统计概览
- 支持本地文件拖入或服务器路径输入

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.2.5, Spring Data JPA |
| GNSS 引擎 | RTKLIB 2.1.1 (rtklib-core) |
| 数据库 | H2 (嵌入式) |
| 通信 | WebSocket (定位推送), SSE (评估进度) |
| 串口 | jSerialComm 2.10.3 |
| 前端 | 原生 HTML/CSS/JS, Canvas 图表 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+

### 构建与运行

```bash
# 克隆仓库
git clone https://github.com/jinyuttt/rtklib-tuning.git
cd rtklib-tuning

# 构建
mvn clean package -DskipTests

# 运行
java -jar target/rtklib-tuning-1.0.0.jar
```

启动后访问 http://localhost:9999

### 配置

编辑 `src/main/resources/application.properties`：

```properties
# 服务端口
server.port=9999

# 数据根目录（存放观测文件、解算结果等）
tuning.data-root=./data
```

## 项目结构

```
src/main/
├── java/org/rtklib/tuning/
│   ├── TuningApplication.java          # 启动入口
│   ├── controller/
│   │   ├── AnalysisController.java     # 评估与轨迹数据 API
│   │   ├── ConfigController.java       # 基站/参数配置 API
│   │   ├── DeviceController.java       # 串口设备管理 API
│   │   ├── FileController.java         # 文件分析 API
│   │   ├── ParamController.java        # 参数预设 API
│   │   ├── SolveController.java        # 独立定位 API
│   │   └── TaskController.java         # 任务管理 API
│   ├── service/
│   │   ├── AnalysisService.java        # 评估核心逻辑
│   │   ├── PositioningEngine.java      # RTKLIB 定位引擎封装
│   │   ├── Rtcm3Analyzer.java         # RTCM 流分析
│   │   ├── FileIndexService.java       # 文件索引与POS持久化
│   │   └── ...
│   ├── entity/ & dto/ & model/ & repository/
│   └── websocket/
│       └── SolveWsHandler.java         # 定位结果 WebSocket 推送
└── resources/
    ├── application.properties
    └── static/
        ├── index.html                  # 参数调试页面
        ├── solve.html                  # 独立定位页面
        ├── file.html                   # 单文件分析页面
        ├── css/
        └── js/
            ├── app.js                  # 调参主逻辑
            ├── charts.js               # 图表绘制
            ├── file.js                 # 文件分析逻辑
            ├── solve.js                # 定位逻辑
            └── utils.js                # 工具函数
```

## API 概览

| 路径 | 说明 |
|------|------|
| `POST /api/analyze/evaluate` | 启动参数评估 |
| `GET  /api/analyze/evaluate-progress` | SSE 评估进度流 |
| `GET  /api/analyze/track-data` | 获取轨迹数据（支持分辨率/天数/最大点数） |
| `GET  /api/analyze/track-files` | 获取轨迹文件列表 |
| `POST /api/solve/run` | 启动独立定位 |
| `POST /api/solve/stop` | 停止定位 |
| `GET  /api/solve/result` | 获取定位结果 |
| `POST /api/file/analyze` | 文件分析（JSON路径 / MultipartFile） |
| `GET  /api/config/basestations` | 基站配置查询 |
| `GET  /api/param/presets` | 参数预设列表 |
| `GET  /api/device/scan` | 扫描串口设备 |

## License

本项目基于 RTKLIB 开发，RTKLIB 采用 BSD-2-Clause 许可证。