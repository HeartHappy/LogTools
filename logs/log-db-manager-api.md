# LogDbManager API 文档

## 目录
- [1. 类概述](#1-类概述)
- [2. 构造函数](#2-构造函数)
- [3. 数据结构](#3-数据结构)
  - [3.1 QueryPageResult - 分页查询结果数据结构](#31-querypageresult---分页查询结果数据结构)
- [4. 核心 API](#4-核心-api)
  - [4.1 getDbFileSize - 获取数据库文件大小](#41-getdbfilesize---获取数据库文件大小)
  - [4.2 insertLog - 插入普通文本日志](#42-insertlog---插入普通文本日志)
  - [4.3 insertFileLog - 插入文件日志并持久化文件](#43-insertfilelog---插入文件日志并持久化文件)
  - [4.4 loadImageBase64 - 加载文件并编码为 Base64 字符串](#44-loadimagebase64---加载文件并编码为-base64-字符串)
  - [4.5 loadImagePreviewData - 获取文件预览数据](#45-loadimagepreviewdata---获取文件预览数据)
  - [4.6 loadImageMimeType - 获取文件 MIME 类型](#46-loadimagemimetype---获取文件-mime-类型)
  - [4.7 queryLogsAdvanced - 高级查询日志记录（支持分页或全量）](#47-querylogsadvanced---高级查询日志记录支持分页或全量)
  - [4.8 queryLogsPageAdvanced - 高级分页查询日志记录](#48-querylogspageadvanced---高级分页查询日志记录)
  - [4.9 getDistinctValues - 获取列的去重值列表](#49-getdistinctvalues---获取列的去重值列表)
  - [4.10 deleteLogs - 删除指定作用域下的日志记录](#410-deletelogs---删除指定作用域下的日志记录)
  - [4.11 clearAllLogs - 清空所有作用域的日志](#411-clearalllogs---清空所有作用域的日志)
  - [4.12 startAutoCleanByDate - 启动按日期自动清理日志任务](#412-startautocleanbydate---启动按日期自动清理日志任务)
  - [4.13 startAutoCleanBySize - 启动按文件大小自动清理日志任务](#413-startautocleanbysize---启动按文件大小自动清理日志任务)
- [5. 内部方法](#5-内部方法)
  - [5.1 getTableName - 根据作用域标签生成表名](#51-gettablename---根据作用域标签生成表名)
  - [5.2 ensureTable - 确保数据库表存在](#52-ensuretable---确保数据库表存在)
  - [5.3 queryLogsAllAdvanced - 高级查询所有日志记录（内部）](#53-querylogsalladvanced---高级查询所有日志记录内部)
  - [5.4 performCleanupByDateRange - 根据日期范围执行日志清理（内部）](#54-performcleanupbydaterange---根据日期范围执行日志清理内部)
  - [5.5 deleteLogsByDate - 删除指定日期前的日志（内部）](#55-deletelogsbydate---删除指定日期前的日志内部)
  - [5.6 performCleanupBySize - 根据文件大小执行日志清理（内部）](#56-performcleanupbysize---根据文件大小执行日志清理内部)
  - [5.7 queryStoredFile - 查询指定日志 ID 对应的存储文件对象（内部）](#57-querystoredfile---查询指定日志-id-对应的存储文件对象内部)
  - [5.8 queryFilePathById - 根据日志 ID 查询文件路径（内部）](#58-queryfilepathbyid---根据日志-id-查询文件路径内部)
  - [5.9 queryLogById - 根据日志 ID 查询单条日志记录（内部）](#59-querylogbyid---根据日志-id-查询单条日志记录内部)
  - [5.10 queryFilePaths - 查询指定条件下的文件路径列表（内部）](#510-queryfilepaths---查询指定条件下的文件路径列表内部)
  - [5.11 deleteStoredFiles - 删除文件系统中的文件（内部）](#511-deletestoredfiles---删除文件系统中的文件内部)
  - [5.12 cursorToMap - 将 Cursor 转换为 Map（内部）](#512-cursortomap---将-cursor-转换为-map内部)
  - [5.13 estimateRowBytes - 估算单行日志数据字节数（内部）](#513-estimatorowbytes---估算单行日志数据字节数内部)
  - [5.14 tableExists - 检查数据库表是否存在（内部）](#514-tableexists---检查数据库表是否存在内部)
  - [5.15 getAllLogTables - 获取所有日志表的名称（内部）](#515-getalllogtables---获取所有日志表的名称内部)
  - [5.16 notifyDataChanged - 通知数据已更改（内部）](#516-notifydatachanged---通知数据已更改内部)
  - [5.17 runInTransaction - 在事务中执行数据库操作（内部）](#517-runintransaction---在事务中执行数据库操作内部)
- [6. 数据库操作说明](#6-数据库操作说明)
  - [6.1 表结构](#61-表结构)
  - [6.2 索引](#62-索引)
- [7. 事务处理](#7-事务处理)
- [8. 错误码定义](#8-错误码定义)
- [9. 功能分类索引](#9-功能分类索引)
  - [9.1 查询类方法](#91-查询类方法)
  - [9.2 插入类方法](#92-插入类方法)
  - [9.3 删除类方法](#93-删除类方法)
  - [9.4 清理类方法](#94-清理类方法)
  - [9.5 文件操作类方法](#95-文件操作类方法)
  - [9.6 内部辅助方法](#96-内部辅助方法)

---

<a id="1-类概述"></a>
## 1. 类概述
`LogDbManager` 是 `LoggerX` 日志框架的数据库管理核心组件，负责所有日志数据的持久化、查询、删除和自动清理。它以单例对象（`object`）的形式提供，确保数据库操作的全局一致性。该组件封装了 SQLite 数据库的底层操作，提供了高级的日志管理功能，包括文件日志的存储与预览、复杂查询、基于时间或大小的自动清理机制，并确保了操作的线程安全。

[返回目录](#目录)

<a id="2-构造函数"></a>
## 2. 构造函数
`LogDbManager` 是一个 Kotlin `object`，因此它没有显式的构造函数。其内部的 `dbHelper` 和 `database` 实例在对象首次访问时进行初始化。

[返回目录](#目录)

<a id="3-数据结构"></a>
## 3. 数据结构

<a id="31-querypageresult---分页查询结果数据结构"></a>
### 3.1 QueryPageResult - 分页查询结果数据结构
**功能摘要**: 用于封装分页查询结果的数据类，提供当前页数据、总数、分页信息等。

| 字段名称 | 类型 | 说明 |
| :--- | :--- | :--- |
| `rows` | `List<Map<String, Any>>` | 当前页的日志数据列表。 |
| `totalCount` | `Int` | 符合查询条件的总记录数。 |
| `page` | `Int` | 当前页码。 |
| `limit` | `Int` | 每页的记录限制数。 |
| `nextPage` | `Int?` | 如果有下一页，则为下一页的页码；否则为 `null`。 |
| `approxBytes` | `Int` | 当前页数据的大致字节数，用于估算。 |
| `hasMore` | `Boolean` | 是否还有更多数据页。 |
| `queryPlan` | `List<String>` | 查询计划（目前为空列表）。 |

[返回目录](#目录)

<a id="4-核心-api"></a>
## 4. 核心 API

<a id="41-getdbfilesize---获取数据库文件大小"></a>
### 4.1 getDbFileSize - 获取数据库文件大小
**功能摘要**: 获取当前日志数据库文件的大小，以兆字节 (MB) 为单位。

- **方法签名**: `fun getDbFileSize(): Double`
- **使用场景描述**: 监控数据库存储占用，作为自动清理策略的触发条件。
- **性能注意事项**: 内部调用 `LogDbHelper` 获取文件大小，性能开销很小。
- **异常处理**: 内部已处理文件操作可能出现的异常。

[返回目录](#目录)

<a id="42-insertlog---插入普通文本日志"></a>
### 4.2 insertLog - 插入普通文本日志
**功能摘要**: 插入一条普通文本日志记录到数据库，确保线程安全。

- **方法签名**: `fun insertLog(scopeTag: String, level: String, classTag: String, method: String, message: String): Unit`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
  | `level` | `String` | 是 | 日志级别（如 "D", "I", "W", "E"）。 |
  | `classTag` | `String` | 是 | 日志产生的类标签。 |
  | `method` | `String` | 是 | 日志产生的方法名。 |
  | `message` | `String` | 是 | 日志消息内容。 |
- **使用场景描述**: 记录应用程序中的常规文本信息、调试信息、警告或错误。
- **性能注意事项**: 涉及数据库插入操作，`@Synchronized` 关键字确保了多线程环境下的数据一致性，但可能引入轻微的同步开销。
- **异常处理**: 捕获数据库插入过程中可能发生的 `Exception`，并记录错误日志。
- **参见**: [insertFileLog - 插入文件日志并持久化文件](#43-insertfilelog---插入文件日志并持久化文件)

[返回目录](#目录)

<a id="43-insertfilelog---插入文件日志并持久化文件"></a>
### 4.3 insertFileLog - 插入文件日志并持久化文件
**功能摘要**: 插入一条文件日志记录到数据库，并负责将源文件持久化到内部存储，确保线程安全。

- **方法签名**: `fun insertFileLog(scopeTag: String, level: String, classTag: String, method: String, message: String, sourceFile: File): Map<String, Any>`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
  | `level` | `String` | 是 | 日志级别。 |
  | `classTag` | `String` | 是 | 日志产生的类标签。 |
  | `method` | `String` | 是 | 日志产生的方法名。 |
  | `message` | `String` | 是 | 日志消息内容。 |
  | `sourceFile` | `File` | 是 | 原始文件对象，将被持久化。 |
- **使用场景描述**: 记录与特定文件（如图片、配置文件、崩溃日志文件等）相关的事件。
- **性能注意事项**: 涉及文件复制（`FileLogStorageManager.persistSource`）和数据库插入操作。文件操作可能相对耗时，`@Synchronized` 确保线程安全。
- **异常处理**: 捕获文件持久化或数据库插入过程中可能发生的 `Exception`。如果插入失败，会尝试删除已持久化的文件，并抛出 `FileLogWriteException`。
- **参见**: [insertLog - 插入普通文本日志](#42-insertlog---插入普通文本日志)

[返回目录](#目录)

<a id="44-loadimagebase64---加载文件并编码为-base64-字符串"></a>
### 4.4 loadImageBase64 - 加载文件并编码为 Base64 字符串
**功能摘要**: 根据日志 ID 加载对应的文件内容，并将其编码为 Base64 字符串。

- **方法签名**: `fun loadImageBase64(scopeTag: String, logId: Int): String?`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
  | `logId` | `Int` | 是 | 日志记录的唯一 ID。 |
- **使用场景描述**: 在 Web 视图或需要 Base64 编码的场景中显示图片或其他文件内容。
- **性能注意事项**: 读取文件内容并进行 Base64 编码，对于大文件可能导致内存消耗增加和性能下降。建议仅用于小文件或预览场景。
- **异常处理**: 捕获文件读取和 Base64 编码过程中可能发生的 `Exception`，并记录错误日志。
- **参见**: [loadImagePreviewData - 获取文件预览数据](#45-loadimagepreviewdata---获取文件预览数据), [loadImageMimeType - 获取文件 MIME 类型](#46-loadimagemimetype---获取文件-mime-类型)

[返回目录](#目录)

<a id="45-loadimagepreviewdata---获取文件预览数据"></a>
### 4.5 loadImagePreviewData - 获取文件预览数据
**功能摘要**: 根据日志 ID 获取文件预览所需的数据，包括文件路径和 MIME 类型。

- **方法签名**: `fun loadImagePreviewData(scopeTag: String, logId: Int): ImagePreviewData?`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
  | `logId` | `Int` | 是 | 日志记录的唯一 ID。 |
- **使用场景描述**: 在 UI 中展示文件预览时，获取文件的基本信息，避免一次性加载整个文件内容。
- **性能注意事项**: 涉及数据库查询和文件 MIME 类型检测，性能较好，避免了不必要的文件内容加载。
- **异常处理**: 内部调用 `queryStoredFile`，其已处理文件查找异常。
- **参见**: [loadImageBase64 - 加载文件并编码为 Base64 字符串](#44-loadimagebase64---加载文件并编码为-base64-字符串), [loadImageMimeType - 获取文件 MIME 类型](#46-loadimagemimetype---获取文件-mime-类型)

[返回目录](#目录)

<a id="46-loadimagemimetype---获取文件-mime-类型"></a>
### 4.6 loadImageMimeType - 获取文件 MIME 类型
**功能摘要**: 根据日志 ID 获取对应文件的 MIME 类型。

- **方法签名**: `fun loadImageMimeType(scopeTag: String, logId: Int): String?`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
  | `logId` | `Int` | 是 | 日志记录的唯一 ID。 |
- **使用场景描述**: 根据文件类型进行不同的处理或显示逻辑。
- **性能注意事项**: 涉及数据库查询和文件 MIME 类型检测，性能较好。
- **异常处理**: 内部调用 `queryStoredFile`，其已处理文件查找异常。
- **参见**: [loadImageBase64 - 加载文件并编码为 Base64 字符串](#44-loadimagebase64---加载文件并编码为-base64-字符串), [loadImagePreviewData - 获取文件预览数据](#45-loadimagepreviewdata---获取文件预览数据)

[返回目录](#目录)

<a id="47-querylogsadvanced---高级查询日志记录支持分页或全量"></a>
### 4.7 queryLogsAdvanced - 高级查询日志记录（支持分页或全量）
**功能摘要**: 高级查询日志记录，支持多种过滤条件和分页功能。如果 `limit` 为 `null`，则查询所有符合条件的记录。

- **方法签名**: `fun queryLogsAdvanced(scopeTag: String, time: String? = null, tag: String? = null, level: String? = null, method: String? = null, isImage: Boolean? = null, keyword: String? = null, isAsc: Boolean = false, page: Int = 1, limit: Int? = 100): List<Map<String, Any>>`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 默认值 | 参数说明 |
  | :--- | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | - | 日志所属的作用域标签。 |
  | `time` | `String?` | 否 | `null` | 按时间过滤（前缀匹配，如 "2023-10-26"）。 |
  | `tag` | `String?` | 否 | `null` | 按类标签精确匹配过滤。 |
  | `level` | `String?` | 否 | `null` | 按日志级别精确匹配过滤。 |
  | `method` | `String?` | 否 | `null` | 按方法名模糊匹配过滤。 |
  | `isImage` | `Boolean?` | 否 | `null` | 是否为文件日志（`true` 表示文件日志，`false` 表示普通日志）。 |
  | `keyword` | `String?` | 否 | `null` | 按消息内容关键字模糊匹配过滤。 |
  | `isAsc` | `Boolean` | 否 | `false` | 排序方式，`true` 为升序，`false` 为降序（基于时间）。 |
  | `page` | `Int` | 否 | `1` | 查询的页码。 |
  | `limit` | `Int?` | 否 | `100` | 每页返回的记录数。如果为 `null`，则返回所有符合条件的记录。 |
- **使用场景描述**: 日志查看界面，提供灵活的日志搜索和筛选功能。
- **性能注意事项**: 复杂的 SQL 查询，性能取决于查询条件、数据量和索引使用情况。当 `limit` 为 `null` 时，可能返回大量数据，需注意内存消耗。
- **异常处理**: 内部调用 [queryLogsAllAdvanced - 高级查询所有日志记录（内部）](#53-querylogsalladvanced---高级查询所有日志记录内部) 或 [queryLogsPageAdvanced - 高级分页查询日志记录](#48-querylogspageadvanced---高级分页查询日志记录)，其内部已处理异常并记录日志。
- **参见**: [queryLogsPageAdvanced - 高级分页查询日志记录](#48-querylogspageadvanced---高级分页查询日志记录), [getDistinctValues - 获取列的去重值列表](#49-getdistinctvalues---获取列的去重值列表)

[返回目录](#目录)

<a id="48-querylogspageadvanced---高级分页查询日志记录"></a>
### 4.8 queryLogsPageAdvanced - 高级分页查询日志记录
**功能摘要**: 高级分页查询日志记录，支持多种过滤条件，并返回包含分页信息的 `QueryPageResult` 对象。此方法会根据 `maxPageBytes` 限制单页返回数据的近似大小。

- **方法签名**: `fun queryLogsPageAdvanced(scopeTag: String, time: String? = null, tag: String? = null, level: String? = null, method: String? = null, isImage: Boolean? = null, keyword: String? = null, isAsc: Boolean = false, page: Int = 1, limit: Int = 100, maxPageBytes: Int = 1024 * 1024): QueryPageResult`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 默认值 | 参数说明 |
  | :--- | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | - | 日志所属的作用域标签。 |
  | `time` | `String?` | 否 | `null` | 按时间过滤（前缀匹配）。 |
  | `tag` | `String?` | 否 | `null` | 按类标签精确匹配过滤。 |
  | `level` | `String?` | 否 | `null` | 按日志级别精确匹配过滤。 |
  | `method` | `String?` | 否 | `null` | 按方法名模糊匹配过滤。 |
  | `isImage` | `Boolean?` | 否 | `null` | 是否为文件日志。 |
  | `keyword` | `String?` | 否 | `null` | 按消息内容关键字模糊匹配过滤。 |
  | `isAsc` | `Boolean` | 否 | `false` | 排序方式，`true` 为升序，`false` 为降序。 |
  | `page` | `Int` | 否 | `1` | 查询的页码。 |
  | `limit` | `Int` | 否 | `100` | 每页返回的记录数。 |
  | `maxPageBytes` | `Int` | 否 | `1024 * 1024` | 单页数据最大允许的近似字节数（默认为 1MB），用于防止单页数据过大。 |
- **使用场景描述**: 日志查看界面，需要精确控制分页和单页数据量的场景，例如在内存受限的环境下。
- **性能注意事项**: 复杂的 SQL 查询，并包含数据量估算逻辑，以避免单页数据过大。性能取决于查询条件、数据量和索引使用情况。
- **异常处理**: 捕获 `Exception` 并记录日志，返回空的 `QueryPageResult` 对象。
- **参见**: [queryLogsAdvanced - 高级查询日志记录（支持分页或全量）](#47-querylogsadvanced---高级查询日志记录支持分页或全量), [getDistinctValues - 获取列的去重值列表](#49-getdistinctvalues---获取列的去重值列表)

[返回目录](#目录)

<a id="49-getdistinctvalues---获取列的去重值列表"></a>
### 4.9 getDistinctValues - 获取列的去重值列表
**功能摘要**: 获取指定作用域下某个列的去重值列表，对特定列有特殊处理逻辑。

- **方法签名**: `fun getDistinctValues(scopeTag: String, columnName: String): List<String>`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
  | `columnName` | `String` | 是 | 要获取去重值的列名（例如 `LoggerX.COLUMN_TIME`, `LoggerX.COLUMN_METHOD`, `LoggerX.COLUMN_IS_IMAGE`）。 |
- **使用场景描述**: 在日志筛选器中提供可供用户选择的去重选项，例如所有不同的日志级别、方法名或日期。
- **性能注意事项**: 数据库 `DISTINCT` 查询，对于包含大量重复数据或大表的列，性能开销可能较大。
- **异常处理**: 捕获 `Exception` 并记录日志，返回空列表。
- **参见**: [queryLogsAdvanced - 高级查询日志记录（支持分页或全量）](#47-querylogsadvanced---高级查询日志记录支持分页或全量)

[返回目录](#目录)

<a id="410-deletelogs---删除指定作用域下的日志记录"></a>
### 4.10 deleteLogs - 删除指定作用域下的日志记录
**功能摘要**: 删除指定作用域下符合条件的日志记录，并同步删除对应的文件。此操作在事务中执行。

- **方法签名**: `fun deleteLogs(scopeTag: String, timeFormat: String?): Int`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
  | `timeFormat` | `String?` | 否 | `null` | 时间格式字符串，用于删除早于该时间的日志。如果为 `null` 或空，则删除该作用域下所有日志。 |
- **使用场景描述**: 清理特定作用域或特定时间段的日志，例如删除一周前的日志。
- **性能注意事项**: 涉及数据库事务和文件删除操作。对于大量日志的删除，可能需要一定时间。
- **异常处理**: 内部通过 [runInTransaction - 在事务中执行数据库操作（内部）](#517-runintransaction---在事务中执行数据库操作内部) 确保事务的原子性，并调用 [deleteStoredFiles - 删除文件系统中的文件（内部）](#511-deletestoredfiles---删除文件系统中的文件内部) 处理文件删除异常。
- **参见**: [clearAllLogs - 清空所有作用域的日志](#411-clearalllogs---清空所有作用域的日志)

[返回目录](#目录)

<a id="411-clearalllogs---清空所有作用域的日志"></a>
### 4.11 clearAllLogs - 清空所有作用域的日志
**功能摘要**: 清空所有作用域下的日志记录和对应的文件。此操作在事务中执行。

- **方法签名**: `fun clearAllLogs(): Boolean`
- **参数**: 无。
- **使用场景描述**: 全局日志清理，例如在应用重置或卸载前。
- **性能注意事项**: 遍历所有日志表，删除所有记录和文件，涉及大量数据库和文件系统操作，可能耗时较长。
- **异常处理**: 捕获 `Exception` 并记录错误日志，返回 `false`。
- **参见**: [deleteLogs - 删除指定作用域下的日志记录](#410-deletelogs---删除指定作用域下的日志记录)

[返回目录](#目录)

<a id="412-startautocleanbydate---启动按日期自动清理日志任务"></a>
### 4.12 startAutoCleanByDate - 启动按日期自动清理日志任务
**功能摘要**: 启动按日期自动清理日志的任务。任务会定期（每 24 小时）执行，清理超过指定保留天数的日志。

- **方法签名**: `fun startAutoCleanByDate(retentionDays: Int): Unit`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `retentionDays` | `Int` | 是 | 日志保留天数。必须大于 0。 |
- **使用场景描述**: 配置日志自动清理策略，确保日志数据不会无限增长。
- **性能注意事项**: 定期任务，清理操作可能涉及大量数据库和文件操作。如果 `retentionDays` 设置过小，可能导致频繁清理。
- **异常处理**: 内部捕获 [performCleanupByDateRange - 根据日期范围执行日志清理（内部）](#54-performcleanupbydaterange---根据日期范围执行日志清理内部) 执行过程中可能发生的 `Exception` 并记录日志。
- **参见**: [startAutoCleanBySize - 启动按文件大小自动清理日志任务](#413-startautocleanbysize---启动按文件大小自动清理日志任务)

[返回目录](#目录)

<a id="413-startautocleanbysize---启动按文件大小自动清理日志任务"></a>
### 4.13 startAutoCleanBySize - 启动按文件大小自动清理日志任务
**功能摘要**: 启动按数据库文件大小自动清理日志的任务。当数据库文件大小超过 `maxSizeMb` 时，会尝试清理日志。

- **方法签名**: `fun startAutoCleanBySize(maxSizeMb: Double, cleanSizeMb: Double): Unit`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `maxSizeMb` | `Double` | 是 | 数据库文件最大允许大小 (MB)。必须大于 0。 |
  | `cleanSizeMb` | `Double` | 是 | 每次清理尝试减少的大小 (MB)。必须大于 0。 |
- **使用场景描述**: 配置日志自动清理策略，控制数据库文件大小，防止占用过多存储空间。
- **性能注意事项**: 定期任务，清理操作可能涉及大量数据库和文件操作。清理逻辑会循环删除旧日志直到达到目标大小或达到最大循环次数。
- **异常处理**: 内部捕获 [performCleanupBySize - 根据文件大小执行日志清理（内部）](#56-performcleanupbysize---根据文件大小执行日志清理内部) 执行过程中可能发生的 `Exception` 并记录日志。
- **参见**: [startAutoCleanByDate - 启动按日期自动清理日志任务](#412-startautocleanbydate---启动按日期自动清理日志任务)

[返回目录](#目录)

<a id="5-内部方法"></a>
## 5. 内部方法

<a id="51-gettablename---根据作用域标签生成表名"></a>
### 5.1 getTableName - 根据作用域标签生成表名
**功能摘要**: 根据作用域标签生成数据库表名，将标签中非字母数字下划线的字符替换为下划线。

- **方法签名**: `private fun getTableName(scopeTag: String): String`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
- **使用场景描述**: 内部方法，用于生成日志存储的表名，确保表名符合 SQLite 命名规范。
- **性能注意事项**: 字符串替换操作，性能开销小。
- **异常处理**: 无显式异常处理。

[返回目录](#目录)

<a id="52-ensuretable---确保数据库表存在"></a>
### 5.2 ensureTable - 确保数据库表存在
**功能摘要**: 确保数据库表存在。如果表名不在 `existedTables` 缓存中，则创建表并添加到缓存。此方法是线程安全的。

- **方法签名**: `@Synchronized private fun ensureTable(tableName: String): Unit`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `tableName` | `String` | 是 | 要检查或创建的表名。 |
- **使用场景描述**: 内部方法，在执行数据库操作（如插入）前调用，保证表结构存在。
- **性能注意事项**: 使用 `existedTables` 缓存避免重复创建表，提高效率。`@Synchronized` 保证线程安全。
- **异常处理**: 内部调用 `dbHelper.createLogTable`，其可能抛出异常。

[返回目录](#目录)

<a id="53-querylogsalladvanced---高级查询所有日志记录内部"></a>
### 5.3 queryLogsAllAdvanced - 高级查询所有日志记录（内部）
**功能摘要**: 高级查询所有符合条件的日志记录，不进行分页。此方法是 `queryLogsAdvanced` 在 `limit` 为 `null` 时的内部实现。

- **方法签名**: `private fun queryLogsAllAdvanced(scopeTag: String, time: String? = null, tag: String? = null, level: String? = null, method: String? = null, isImage: Boolean? = null, keyword: String? = null, isAsc: Boolean = false): List<Map<String, Any>>`
- **参数详情**: 同 [queryLogsPageAdvanced - 高级分页查询日志记录](#48-querylogspageadvanced---高级分页查询日志记录)，但无 `page`, `limit`, `maxPageBytes` 参数。
- **使用场景描述**: 内部方法，当需要获取所有符合条件的日志时调用。
- **性能注意事项**: 可能返回大量数据，需注意内存消耗。复杂的 SQL 查询，性能取决于查询条件和数据量。
- **异常处理**: 捕获 `Exception` 并记录日志，返回空列表。
- **参见**: [queryLogsAdvanced - 高级查询日志记录（支持分页或全量）](#47-querylogsadvanced---高级查询日志记录支持分页或全量)

[返回目录](#目录)

<a id="54-performcleanupbydaterange---根据日期范围执行日志清理内部"></a>
### 5.4 performCleanupByDateRange - 根据日期范围执行日志清理（内部）
**功能摘要**: 根据日期范围执行日志清理。遍历所有日志表，找出超过保留天数的旧数据并删除。

- **方法签名**: `private fun performCleanupByDateRange(days: Int): Unit`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `days` | `Int` | 是 | 日志保留天数。 |
- **使用场景描述**: 自动清理任务 [startAutoCleanByDate - 启动按日期自动清理日志任务](#412-startautocleanbydate---启动按日期自动清理日志任务) 内部调用。
- **性能注意事项**: 遍历所有日志表，查询日期，然后删除旧数据，可能涉及大量数据库操作。
- **异常处理**: 无显式异常处理，由调用方 `startAutoCleanByDate` 捕获并记录日志。
- **参见**: [deleteLogsByDate - 删除指定日期前的日志（内部）](#55-deletelogsbydate---删除指定日期前的日志内部)

[返回目录](#目录)

<a id="55-deletelogsbydate---删除指定日期前的日志内部"></a>
### 5.5 deleteLogsByDate - 删除指定日期前的日志（内部）
**功能摘要**: 删除指定表中小于或等于截止日期的日志记录，并同步删除对应的文件。此操作在事务中执行。

- **方法签名**: `private fun deleteLogsByDate(tableName: String, cutoffDate: String): Unit`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `tableName` | `String` | 是 | 要操作的表名。 |
  | `cutoffDate` | `String` | 是 | 截止日期字符串（格式为 YYYY-MM-DD）。 |
- **使用场景描述**: [performCleanupByDateRange - 根据日期范围执行日志清理（内部）](#54-performcleanupbydaterange---根据日期范围执行日志清理内部) 内部调用。
- **性能注意事项**: 涉及文件删除和数据库删除事务。
- **异常处理**: 无显式异常处理。
- **参见**: [deleteLogs - 删除指定作用域下的日志记录](#410-deletelogs---删除指定作用域下的日志记录)

[返回目录](#目录)

<a id="56-performcleanupbysize---根据文件大小执行日志清理内部"></a>
### 5.6 performCleanupBySize - 根据文件大小执行日志清理（内部）
**功能摘要**: 根据数据库文件大小执行日志清理。当数据库文件大小超过 `maxSizeMb` 时，会循环删除旧日志（每次 500 条）直到达到目标大小或达到最大循环次数。

- **方法签名**: `private fun performCleanupBySize(maxSizeMb: Double, cleanSizeMb: Double): Unit`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `maxSizeMb` | `Double` | 是 | 数据库文件最大允许大小 (MB)。 |
  | `cleanSizeMb` | `Double` | 是 | 每次清理尝试减少的大小 (MB)。 |
- **使用场景描述**: 自动清理任务 [startAutoCleanBySize - 启动按文件大小自动清理日志任务](#413-startautocleanbysize---启动按文件大小自动清理日志任务) 内部调用。
- **性能注意事项**: 循环清理，每次删除 500 条记录，直到达到目标大小或循环次数限制。涉及文件删除和数据库删除事务。
- **异常处理**: 无显式异常处理，由调用方 `startAutoCleanBySize` 捕获并记录日志。

[返回目录](#目录)

<a id="57-querystoredfile---查询指定日志-id-对应的存储文件对象内部"></a>
### 5.7 queryStoredFile - 查询指定日志 ID 对应的存储文件对象（内部）
**功能摘要**: 查询指定日志 ID 对应的存储文件对象。

- **方法签名**: `private fun queryStoredFile(scopeTag: String, logId: Int): File?`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `scopeTag` | `String` | 是 | 日志所属的作用域标签。 |
  | `logId` | `Int` | 是 | 日志记录的唯一 ID。 |
- **使用场景描述**: 内部方法，用于获取文件日志的实际存储文件，供 [loadImageBase64 - 加载文件并编码为 Base64 字符串](#44-loadimagebase64---加载文件并编码为-base64-字符串) 等方法调用。
- **性能注意事项**: 涉及数据库查询和文件系统操作。
- **异常处理**: 捕获 `Exception` 并记录错误日志，返回 `null`。
- **参见**: [queryFilePathById - 根据日志 ID 查询文件路径（内部）](#58-queryfilepathbyid---根据日志-id-查询文件路径内部)

[返回目录](#目录)

<a id="58-queryfilepathbyid---根据日志-id-查询文件路径内部"></a>
### 5.8 queryFilePathById - 根据日志 ID 查询文件路径（内部）
**功能摘要**: 根据日志 ID 查询指定表中的文件路径。

- **方法签名**: `private fun queryFilePathById(tableName: String, logId: Int): String?`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `tableName` | `String` | 是 | 要查询的表名。 |
  | `logId` | `Int` | 是 | 日志记录的唯一 ID。 |
- **使用场景描述**: 内部方法，供 [queryStoredFile - 查询指定日志 ID 对应的存储文件对象（内部）](#57-querystoredfile---查询指定日志-id-对应的存储文件对象内部) 调用。
- **性能注意事项**: 数据库查询。
- **异常处理**: 捕获 `Exception` 并记录错误日志，返回 `null`。

[返回目录](#目录)

<a id="59-querylogbyid---根据日志-id-查询单条日志记录内部"></a>
### 5.9 queryLogById - 根据日志 ID 查询单条日志记录（内部）
**功能摘要**: 根据日志 ID 查询指定表中的单条日志记录。

- **方法签名**: `private fun queryLogById(tableName: String, logId: Int): Map<String, Any>?`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `tableName` | `String` | 是 | 要查询的表名。 |
  | `logId` | `Int` | 是 | 日志记录的唯一 ID。 |
- **使用场景描述**: 内部方法，用于获取单条日志的详细信息。
- **性能注意事项**: 数据库查询。
- **异常处理**: 捕获 `Exception` 并记录错误日志，返回 `null`。

[返回目录](#目录)

<a id="510-queryfilepaths---查询指定条件下的文件路径列表内部"></a>
### 5.10 queryFilePaths - 查询指定条件下的文件路径列表（内部）
**功能摘要**: 查询指定表和条件下所有文件日志的路径。

- **方法签名**: `private fun queryFilePaths(tableName: String, where: String?, args: Array<String>?): List<String>`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `tableName` | `String` | 是 | 要查询的表名。 |
  | `where` | `String?` | 否 | `null` | SQL WHERE 子句。 |
  | `args` | `Array<String>?` | 否 | `null` | WHERE 子句的参数数组。 |
- **使用场景描述**: 内部方法，用于获取需要删除的文件路径，例如在删除数据库日志时。
- **性能注意事项**: 数据库查询。
- **异常处理**: 捕获 `Exception` 并记录错误日志，返回空列表。
- **参见**: [deleteStoredFiles - 删除文件系统中的文件（内部）](#511-deletestoredfiles---删除文件系统中的文件内部)

[返回目录](#目录)

<a id="511-deletestoredfiles---删除文件系统中的文件内部"></a>
### 5.11 deleteStoredFiles - 删除文件系统中的文件（内部）
**功能摘要**: 删除存储在文件系统中的文件。

- **方法签名**: `private fun deleteStoredFiles(filePaths: Collection<String>): Unit`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `filePaths` | `Collection<String>` | 是 | 要删除的文件路径集合。 |
- **使用场景描述**: 内部方法，在删除数据库日志时同步删除对应的文件，以释放存储空间。
- **性能注意事项**: 遍历文件路径并执行文件删除操作。对于大量文件，可能耗时较长。
- **异常处理**: 捕获文件删除过程中可能发生的 `Exception`，并记录警告日志。
- **参见**: [deleteLogs - 删除指定作用域下的日志记录](#410-deletelogs---删除指定作用域下的日志记录), [clearAllLogs - 清空所有作用域的日志](#411-clearalllogs---清空所有作用域的日志)

[返回目录](#目录)

<a id="512-cursortomap---将-cursor-转换为-map内部"></a>
### 5.12 cursorToMap - 将 Cursor 转换为 Map（内部）
**功能摘要**: 将 SQLite 数据库查询结果的 `Cursor` 对象转换为 `Map<String, Any>`。此方法会处理 `COLUMN_FILE_PATH` 和 `COLUMN_IS_IMAGE` 虚拟列。

- **方法签名**: `private fun cursorToMap(c: Cursor): Map<String, Any>`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `c` | `Cursor` | 是 | 数据库查询结果的游标。 |
- **使用场景描述**: 内部方法，用于将数据库查询结果转换为统一的 Map 格式，方便上层业务逻辑处理。
- **性能注意事项**: 遍历 Cursor 列，进行类型转换。性能开销较小。
- **异常处理**: `getColumnIndexOrThrow` 可能抛出异常，但通常在已知列名的情况下不会发生。

[返回目录](#目录)

<a id="513-estimatorowbytes---估算单行日志数据字节数内部"></a>
### 5.13 estimateRowBytes - 估算单行日志数据字节数（内部）
**功能摘要**: 估算单行日志数据的大致字节数。用于分页查询时控制单页数据大小。

- **方法签名**: `private fun estimateRowBytes(row: Map<String, Any>): Int`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `row` | `Map<String, Any>` | 是 | 单行日志数据。 |
- **使用场景描述**: 内部方法，用于 [queryLogsPageAdvanced - 高级分页查询日志记录](#48-querylogspageadvanced---高级分页查询日志记录) 估算分页数据大小，以避免单页数据过大。
- **性能注意事项**: 遍历 Map 键值对，进行字符串长度计算。性能开销较小。
- **异常处理**: 无。

[返回目录](#目录)

<a id="514-tableexists---检查数据库表是否存在内部"></a>
### 5.14 tableExists - 检查数据库表是否存在（内部）
**功能摘要**: 检查数据库表是否存在。使用 `existedTables` 缓存进行优化。

- **方法签名**: `private fun tableExists(tableName: String): Boolean`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `tableName` | `String` | 是 | 要检查的表名。 |
- **使用场景描述**: 内部方法，在执行数据库操作前检查表是否存在。
- **性能注意事项**: 数据库查询，使用 `existedTables` 缓存优化，减少实际的数据库查询次数。
- **异常处理**: 捕获 `Exception` 并记录错误日志，返回 `false`。
- **参见**: [ensureTable - 确保数据库表存在](#52-ensuretable---确保数据库表存在)

[返回目录](#目录)

<a id="515-getalllogtables---获取所有日志表的名称内部"></a>
### 5.15 getAllLogTables - 获取所有日志表的名称（内部）
**功能摘要**: 获取所有以 `_log` 结尾的日志表的名称。

- **方法签名**: `private fun getAllLogTables(): List<String>`
- **参数**: 无。
- **使用场景描述**: 内部方法，用于全局清理操作，例如 [clearAllLogs - 清空所有作用域的日志](#411-clearalllogs---清空所有作用域的日志)。
- **性能注意事项**: 数据库查询。
- **异常处理**: 无显式异常处理。

[返回目录](#目录)

<a id="516-notifydatachanged---通知数据已更改内部"></a>
### 5.16 notifyDataChanged - 通知数据已更改（内部）
**功能摘要**: 通知数据已更改，用于刷新缓存或 UI。内部调用 `DataQueryService.invalidateCache()`。

- **方法签名**: `private fun notifyDataChanged(): Unit`
- **参数**: 无。
- **使用场景描述**: 内部方法，在数据发生增删改后调用，以确保数据一致性。
- **性能注意事项**: 调用 `DataQueryService.invalidateCache()`，具体性能取决于 `DataQueryService` 的实现。
- **异常处理**: 无。

[返回目录](#目录)

<a id="517-runintransaction---在事务中执行数据库操作内部"></a>
### 5.17 runInTransaction - 在事务中执行数据库操作（内部）
**功能摘要**: 在数据库事务中执行给定的代码块，确保操作的原子性。如果代码块成功执行，则提交事务；否则回滚。

- **方法签名**: `private inline fun runInTransaction(block: () -> Int): Int`
- **参数详情**:
  | 参数名称 | 类型 | 是否必填 | 参数说明 |
  | :--- | :--- | :--- | :--- |
  | `block` | `() -> Int` | 是 | 在事务中执行的代码块，应返回受影响的行数。 |
- **使用场景描述**: 内部方法，用于需要事务支持的数据库操作，如批量删除，以保证数据一致性。
- **性能注意事项**: 确保事务的正确开启、提交和结束，提高数据一致性和操作效率。
- **异常处理**: 捕获 `endTransaction` 时的异常，确保事务关闭。
- **参见**: [deleteLogs - 删除指定作用域下的日志记录](#410-deletelogs---删除指定作用域下的日志记录), [clearAllLogs - 清空所有作用域的日志](#411-clearalllogs---清空所有作用域的日志)

[返回目录](#目录)

<a id="6-数据库操作说明"></a>
## 6. 数据库操作说明

`LogDbManager` 内部使用 `SQLiteDatabase` 进行所有数据库操作。每个日志作用域（Scope）对应一个独立的数据库表，表名通过 [getTableName - 根据作用域标签生成表名](#51-gettablename---根据作用域标签生成表名) 方法生成。所有对数据库的写入操作（[insertLog - 插入普通文本日志](#42-insertlog---插入普通文本日志), [insertFileLog - 插入文件日志并持久化文件](#43-insertfilelog---插入文件日志并持久化文件), [deleteLogs - 删除指定作用域下的日志记录](#410-deletelogs---删除指定作用域下的日志记录), [clearAllLogs - 清空所有作用域的日志](#411-clearalllogs---清空所有作用域的日志)）都通过 `@Synchronized` 关键字或 [runInTransaction - 在事务中执行数据库操作（内部）](#517-runintransaction---在事务中执行数据库操作内部) 方法确保线程安全和数据一致性。

<a id="61-表结构"></a>
### 6.1 表结构
每个日志表包含以下列：

| 列名 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | `INTEGER` | 主键，自增。 |
| `time` | `TEXT` | 日志记录时间。 |
| `level` | `TEXT` | 日志级别。 |
| `tag` | `TEXT` | 类标签。 |
| `method` | `TEXT` | 方法名。 |
| `message` | `TEXT` | 日志消息内容。 |
| `file_path` | `TEXT` | 文件日志的存储路径（如果是非文件日志则为空）。 |

此外，查询结果中会包含一个虚拟列 `is_image`，用于标识是否为文件日志。

[返回目录](#目录)

<a id="62-索引"></a>
### 6.2 索引
`LogDbHelper` 负责创建和管理数据库索引，以优化查询性能。通常会在 `time`、`level`、`tag` 等常用查询字段上创建索引。

[返回目录](#目录)

<a id="7-事务处理"></a>
## 7. 事务处理

`LogDbManager` 通过 [runInTransaction - 在事务中执行数据库操作（内部）](#517-runintransaction---在事务中执行数据库操作内部) 内部方法来处理需要事务支持的数据库操作，例如批量删除日志。这确保了在执行复杂操作时，要么所有操作都成功，要么所有操作都回滚，从而维护数据库的数据完整性和一致性。

[返回目录](#目录)

<a id="8-错误码定义"></a>
## 8. 错误码定义

`LogDbManager` 在内部通过 `android.util.Log.e` 记录错误信息，并通过 `runCatching` 机制处理可能发生的异常。对于文件日志写入失败，会抛出 `FileLogWriteException`。具体的错误码并未显式定义，而是通过异常类型和日志消息来传达错误信息。

- **`FileLogWriteException`**: 表示文件日志写入失败，可能的原因包括路径非法、权限不足、磁盘已满或目录不可用等。

[返回目录](#目录)

<a id="9-功能分类索引"></a>
## 9. 功能分类索引

<a id="91-查询类方法"></a>
### 9.1 查询类方法
- [getDbFileSize - 获取数据库文件大小](#41-getdbfilesize---获取数据库文件大小)
- [loadImageBase64 - 加载文件并编码为 Base64 字符串](#44-loadimagebase64---加载文件并编码为-base64-字符串)
- [loadImagePreviewData - 获取文件预览数据](#45-loadimagepreviewdata---获取文件预览数据)
- [loadImageMimeType - 获取文件 MIME 类型](#46-loadimagemimetype---获取文件-mime-类型)
- [queryLogsAdvanced - 高级查询日志记录（支持分页或全量）](#47-querylogsadvanced---高级查询日志记录支持分页或全量)
- [queryLogsPageAdvanced - 高级分页查询日志记录](#48-querylogspageadvanced---高级分页查询日志记录)
- [getDistinctValues - 获取列的去重值列表](#49-getdistinctvalues---获取列的去重值列表)
- [queryLogsAllAdvanced - 高级查询所有日志记录（内部）](#53-querylogsalladvanced---高级查询所有日志记录内部)
- [queryStoredFile - 查询指定日志 ID 对应的存储文件对象（内部）](#57-querystoredfile---查询指定日志-id-对应的存储文件对象内部)
- [queryFilePathById - 根据日志 ID 查询文件路径（内部）](#58-queryfilepathbyid---根据日志-id-查询文件路径内部)
- [queryLogById - 根据日志 ID 查询单条日志记录（内部）](#59-querylogbyid---根据日志-id-查询单条日志记录内部)
- [queryFilePaths - 查询指定条件下的文件路径列表（内部）](#510-queryfilepaths---查询指定条件下的文件路径列表内部)
- [tableExists - 检查数据库表是否存在（内部）](#514-tableexists---检查数据库表是否存在内部)
- [getAllLogTables - 获取所有日志表的名称（内部）](#515-getalllogtables---获取所有日志表的名称内部)

[返回目录](#目录)

<a id="92-插入类方法"></a>
### 9.2 插入类方法
- [insertLog - 插入普通文本日志](#42-insertlog---插入普通文本日志)
- [insertFileLog - 插入文件日志并持久化文件](#43-insertfilelog---插入文件日志并持久化文件)

[返回目录](#目录)

<a id="93-删除类方法"></a>
### 9.3 删除类方法
- [deleteLogs - 删除指定作用域下的日志记录](#410-deletelogs---删除指定作用域下的日志记录)
- [clearAllLogs - 清空所有作用域的日志](#411-clearalllogs---清空所有作用域的日志)
- [deleteLogsByDate - 删除指定日期前的日志（内部）](#55-deletelogsbydate---删除指定日期前的日志内部)
- [deleteStoredFiles - 删除文件系统中的文件（内部）](#511-deletestoredfiles---删除文件系统中的文件内部)

[返回目录](#目录)

<a id="94-清理类方法"></a>
### 9.4 清理类方法
- [startAutoCleanByDate - 启动按日期自动清理日志任务](#412-startautocleanbydate---启动按日期自动清理日志任务)
- [startAutoCleanBySize - 启动按文件大小自动清理日志任务](#413-startautocleanbysize---启动按文件大小自动清理日志任务)
- [performCleanupByDateRange - 根据日期范围执行日志清理（内部）](#54-performcleanupbydaterange---根据日期范围执行日志清理内部)
- [performCleanupBySize - 根据文件大小执行日志清理（内部）](#56-performcleanupbysize---根据文件大小执行日志清理内部)

[返回目录](#目录)

<a id="95-文件操作类方法"></a>
### 9.5 文件操作类方法
- [loadImageBase64 - 加载文件并编码为 Base64 字符串](#44-loadimagebase64---加载文件并编码为-base64-字符串)
- [loadImagePreviewData - 获取文件预览数据](#45-loadimagepreviewdata---获取文件预览数据)
- [loadImageMimeType - 获取文件 MIME 类型](#46-loadimagemimetype---获取文件-mime-类型)
- [deleteStoredFiles - 删除文件系统中的文件（内部）](#511-deletestoredfiles---删除文件系统中的文件内部)

[返回目录](#目录)

<a id="96-内部辅助方法"></a>
### 9.6 内部辅助方法
- [getTableName - 根据作用域标签生成表名](#51-gettablename---根据作用域标签生成表名)
- [ensureTable - 确保数据库表存在](#52-ensuretable---确保数据库表存在)
- [cursorToMap - 将 Cursor 转换为 Map（内部）](#512-cursortomap---将-cursor-转换为-map内部)
- [estimateRowBytes - 估算单行日志数据字节数（内部）](#513-estimatorowbytes---估算单行日志数据字节数内部)
- [notifyDataChanged - 通知数据已更改（内部）](#516-notifydatachanged---通知数据已更改内部)
- [runInTransaction - 在事务中执行数据库操作（内部）](#517-runintransaction---在事务中执行数据库操作内部)

[返回目录](#目录)
