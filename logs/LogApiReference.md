# LoggerX Log API Reference

## 1. 变更概览

本版文件日志链路已升级为:

- 不再执行图片压缩、Base64 预编码或二进制落库。
- 每个 `scope` 仍对应一张日志表 `<scope>_log`，文件日志仅保存 `file_path`。
- 原 `log_image` 表、图片 blob 字段和关联索引已全部移除。
- 导出 CSV/TXT 时按 `file_path` 读取文件并输出 `file_base64`:
  `data:image/{ext};base64,<base64_body>`。

## 2. 文件写入策略

- 对外入口改为 `LogScopeProxy.file(path)` 与 `LogScopeProxy.file(file)`。
- 写入前校验空值、路径格式、文件存在性、可读性和非空文件。
- 源文件会复制到统一的可配置目录 `OutputConfig.storageDirPath`。
- 目录不存在时自动创建，并进行读写探测。
- 写入失败时抛 `FileLogWriteException`，错误信息面向业务场景。

## 3. 表结构

### 3.1 Scope 表 `<scope>_log`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `INTEGER PK AUTOINCREMENT` | 日志主键 |
| `time` | `TIMESTAMP` | 写入时间 |
| `level` | `TEXT` | 级别 |
| `tag` | `TEXT` | 类标签 |
| `method` | `TEXT` | 方法 |
| `message` | `TEXT` | 文本消息 |
| `file_path` | `VARCHAR(512)` | 托管后文件绝对路径，空字符串表示非文件日志 |

索引:

- `idx_<scope>_time(time)`
- `idx_<scope>_file_path(file_path)`

## 4. 导出规则

- 列顺序: `scope` 字段在前，文件字段在后。
- 文件列: `file_path`。
- 预览列: `file_base64`。
- 导出过程采用分页、批量、异步处理，并内置失败重试。
- 编码: UTF-8。
- 换行符: CRLF(`\r\n`)。
- CSV: RFC 4180 双引号转义。

## 5. 关键 API

- `LoggerX.init(context, OutputConfig(storageDirPath = ...))`:
  配置统一文件存储目录。
- `LogScopeProxy.file(pathOrFile, message)`:
  文件日志写入入口，返回包含绝对路径的日志条目。
- `LogDbManager.queryLogsAdvanced(...)`:
  查询时返回 `file_path` 与虚拟列 `is_image`。
- `LogDbManager.loadImagePreviewData(scope, logId)`:
  返回 `filePath + mimeType`，供大图预览使用。

## 6. 排查建议

- `FileLogWriteException`:
  优先查看业务错误信息，确认是路径非法、权限不足、磁盘已满还是目录不可用。
- 导出后无法直接预览:
- 检查导出列是否为 `data:image/{ext};base64,...` 完整前缀。
- 查不到图片:
  检查 `file_path` 是否非空且目标文件仍存在。
