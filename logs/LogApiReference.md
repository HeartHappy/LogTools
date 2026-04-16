# LoggerX Log API Reference

## 1. 变更概览

本版图片日志链路已升级为:

- 图片二进制统一写入 SQLite `BLOB`，不再落文件、不再分片表。
- 每个 `scope` 仍对应一张日志表 `<scope>_log`，图片明细统一存储在 `log_image`。
- `scope` 表仅保留 `thumbnail` 和 `image_id` 两个图片关联字段。
- 导出 CSV/TXT 时，`compressed_image` 与 `thumbnail` 均输出为:
  `data:<media_type>;base64,<base64_body>`。

## 2. 图片写入策略

- 默认压缩器: `DefaultImageCompressor` (WebP，质量 85，优先 lossless)。
- 可插拔压缩器: `IImageCompressor`，通过 `LoggerX.setImageCompressor(...)` 注入。
- 压缩流程:
  原始图 -> 自定义压缩器 -> WebP 阶梯降级（质量/分辨率/灰度）-> `compressed_image(BLOB)`。
- 目标约束:
  单图输入 <= 8 MB；目标压缩 <= 800 KB；超限自动继续降级。
- 事务语义:
  压缩与入库在同一事务中执行，失败抛 `ImageLogWriteException` 并携带 `compressionLog`。

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
| `thumbnail` | `TEXT` | 缩略图 base64 |
| `image_id` | `INTEGER DEFAULT -1` | 关联 `log_image.id`，`-1` 表示非图片 |

索引:

- `idx_<scope>_time(time)`
- `idx_<scope>_image_id(image_id)`

### 3.2 图片表 `log_image`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `INTEGER PK AUTOINCREMENT` | 图片主键 |
| `scope_id` | `INTEGER NOT NULL` | 关联 `<scope>_log.id` |
| `media_type` | `TEXT NOT NULL` | MIME，如 `image/webp` |
| `compressed_image` | `BLOB NOT NULL` | 压缩后二进制 |
| `width` | `INTEGER` | 宽 |
| `height` | `INTEGER` | 高 |
| `original_size` | `INTEGER` | 原始字节数 |
| `compressed_size` | `INTEGER` | 压缩后字节数 |
| `compression_ratio` | `REAL` | 压缩比 |
| `checksum_sha256` | `TEXT` | 哈希 |

索引:

- `idx_image_scope_time(scope_id, timestamp)`

## 4. 导出规则

- 列顺序: `scope` 字段在前，图片字段在后。
- `compressed_image`: `BLOB -> Base64(NO_WRAP) -> data URI`。
- `thumbnail`: `Base64 -> data URI`。
- 编码: UTF-8。
- 换行符: CRLF(`\r\n`)。
- CSV: RFC 4180 双引号转义。

## 5. 关键 API

- `LoggerX.setImageCompressor(compressor, options)`:
  注入自定义压缩器和默认参数。
- `LogScopeProxy.image(...)`:
  统一图片写入入口，内部执行事务写库。
- `LogDbManager.queryLogsAdvanced(..., includeImagePayload = true)`:
  查询时可返回 `compressed_image` 的 base64 内容，供导出使用。
- `LogDbManager.loadImagePreviewData(scope, logId)`:
  返回 `mimeType + thumbnailBase64 + compressedBase64`。

## 6. 排查建议

- `ImageLogWriteException`:
  优先查看 `compressionLog`，确认在哪一层降级失败。
- 导出后无法直接预览:
  检查导出列是否为 `data:<media_type>;base64,...` 完整前缀。
- 查不到图片:
  检查 `image_id > 0` 与 `log_image.scope_id` 关联是否一致。
