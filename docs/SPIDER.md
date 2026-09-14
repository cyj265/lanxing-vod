# 爬蟲 API 規格說明

本文件說明如何實作一個 Spider 爬蟲，包含所有方法的參數、回傳格式及 JSON 結構定義。

---

## 目錄

- [概覽](#概覽)
- [爬蟲類型與載入方式](#爬蟲類型與載入方式)
- [Spider 抽象類別](#spider-抽象類別)
    - [init — 初始化](#init--初始化)
    - [homeContent — 首頁分類](#homecontent--首頁分類)
    - [homeVideoContent — 首頁推薦影片](#homevideocontent--首頁推薦影片)
    - [categoryContent — 分類列表](#categorycontent--分類列表)
    - [detailContent — 影片詳情](#detailcontent--影片詳情)
    - [searchContent — 搜尋](#searchcontent--搜尋)
    - [playerContent — 播放解析](#playercontent--播放解析)
    - [liveContent — 直播頻道列表](#livecontent--直播頻道列表)
    - [proxy — 本地代理](#proxy--本地代理)
    - [action — 自定義動作](#action--自定義動作)
    - [manualVideoCheck / isVideoFormat — 影片格式判斷](#manualvideocheck--isvideoformat--影片格式判斷)
    - [destroy — 銷毀](#destroy--銷毀)
- [回傳資料結構](#回傳資料結構)
    - [Result — 通用回傳物件](#result--通用回傳物件)
    - [Vod — 影片卡片物件](#vod--影片卡片物件)
    - [Class — 分類物件](#class--分類物件)
    - [Filter — 篩選器物件](#filter--篩選器物件)
    - [Danmaku — 彈幕物件](#danmaku--彈幕物件)
    - [Sub — 字幕物件](#sub--字幕物件)
    - [Drm — DRM 設定物件](#drm--drm-設定物件)
    - [播放集數格式（vod_play_from / vod_play_url）](#播放集數格式vod_play_from--vod_play_url)
- [完整 JSON 範例](#完整-json-範例)
    - [homeContent 回傳範例](#homecontent-回傳範例)
    - [homeVideoContent / categoryContent 回傳範例](#homevideocontent--categorycontent-回傳範例)
    - [detailContent 回傳範例](#detailcontent-回傳範例)
    - [playerContent 回傳範例](#playercontent-回傳範例)
    - [searchContent 回傳範例](#searchcontent-回傳範例)
    - [liveContent 回傳範例](#livecontent-回傳範例)
- [爬蟲本地代理 URL](#爬蟲本地代理-url)

---

## 概覽

Spider 是應用程式爬蟲的抽象基底類別，位於 `com.github.catvod.crawler.Spider`。每個影片來源（`Site`）對應一個 Spider 實例。

**生命週期：**

```
init(context, ext)
    │
    ├─► homeContent(filter)          首頁分類
    ├─► homeVideoContent()           首頁推薦
    ├─► categoryContent(...)         分類瀏覽
    ├─► detailContent(ids)           影片詳情
    ├─► searchContent(key, quick)    搜尋
    ├─► playerContent(flag, id, ...) 播放解析
    ├─► liveContent(url)             直播解析
    └─► destroy()                    清理資源
```

**欄位：**

| 欄位        | 類型       | 說明                             |
|-----------|----------|--------------------------------|
| `siteKey` | `String` | 由載入器注入，標識此 Spider 服務的來源 `key`。 |

---

## 爬蟲類型與載入方式

在 `sites` 配置中，`type` 欄位決定呼叫方式，`api` 欄位決定載入哪種引擎：

| `type` | `api` 格式        | 引擎                  | 說明                                                         |
|--------|-----------------|---------------------|------------------------------------------------------------|
| `0`    | HTTP URL        | 內建 XML 解析           | 直接 GET 請求，回傳 XML 格式。                                       |
| `1`    | HTTP URL        | 內建 JSON+Filter      | 直接 GET 請求，回傳 JSON 格式，篩選參數以 `f=` 傳遞。                        |
| `3`    | `csp_ClassName` | JAR（DexClassLoader） | 從 `jar` 指定的 .jar 檔載入 `com.github.catvod.spider.ClassName`。 |
| `3`    | `xxx.js`        | JavaScript（QuickJS） | 載入 `.js` 檔作為 Spider。                                       |
| `3`    | `xxx.py`        | Python（Chaquopy）    | 載入 `.py` 檔作為 Spider。                                       |
| `4`    | HTTP URL        | 內建 JSON+Base64 ext  | 同 `1`，擴充參數以 Base64 編碼傳遞（`ext=`）。                           |

> 本文件主要說明 `type=3`（Spider 直接呼叫）的情境。

---

## Spider 抽象類別

所有方法預設回傳空字串 `""`，子類別僅需覆寫所需功能。

---

### init — 初始化

```java
public void init(Context context, String extend) throws Exception
```

**觸發時機：** Spider 實例建立後呼叫一次，用於初始化連線、載入設定等。

| 參數        | 類型        | 說明                                                    |
|-----------|-----------|-------------------------------------------------------|
| `context` | `Context` | Android Context，可取得應用資源、路徑等。                          |
| `extend`  | `String`  | 對應 `Site.ext` 欄位的額外擴充資料，內容由爬蟲自行定義（可為 URL、JSON 字串或路徑）。 |

**回傳：** 無（`void`）

---

### homeContent — 首頁分類

```java
public String homeContent(boolean filter) throws Exception
```

**觸發時機：** 使用者進入首頁時呼叫，取得分類列表（及可選的篩選器）。

| 參數       | 類型        | 說明                                |
|----------|-----------|-----------------------------------|
| `filter` | `boolean` | `true` 表示需要回傳篩選器資料（`filters` 欄位）。 |

**回傳：** JSON 字串，結構為 [Result](#result--通用回傳物件)。

`class`（分類列表）為主要回傳欄位，`filters`（各分類的篩選器定義）為選填。

---

### homeVideoContent — 首頁推薦影片

```java
public String homeVideoContent() throws Exception
```

**觸發時機：** 首頁分類載入完成後呼叫，取得首頁推薦影片列表。

**回傳：** JSON 字串，結構為 [Result](#result--通用回傳物件)。

主要回傳欄位為 `list`（推薦影片列表）。

---

### categoryContent — 分類列表

```java
public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception
```

**觸發時機：** 使用者點擊分類或切換篩選條件時呼叫。

| 參數       | 類型                        | 說明                                                   |
|----------|---------------------------|------------------------------------------------------|
| `tid`    | `String`                  | 分類 ID，對應 `Class.typeId`。                             |
| `pg`     | `String`                  | 頁碼，從 `"1"` 開始。                                       |
| `filter` | `boolean`                 | 是否啟用篩選器。                                             |
| `extend` | `HashMap<String, String>` | 使用者選擇的篩選條件，key 為篩選器 ID，value 為選項 key。為空 `{}` 時表示無篩選。 |

**回傳：** JSON 字串，結構為 [Result](#result--通用回傳物件)。

主要回傳欄位為 `list`（影片列表），選填 `pagecount`（總頁數，用於分頁控制）。

---

### detailContent — 影片詳情

```java
public String detailContent(List<String> ids) throws Exception
```

**觸發時機：** 使用者點擊影片卡片時呼叫，取得完整詳情與播放集數。

| 參數    | 類型             | 說明                                |
|-------|----------------|-----------------------------------|
| `ids` | `List<String>` | 影片 ID 清單，通常只含一個元素，對應 `Vod.vodId`。 |

**回傳：** JSON 字串，結構為 [Result](#result--通用回傳物件)，`list` 陣列中有一個完整的 `Vod` 物件。

主要 Vod 欄位：`vod_id`、`vod_name`、`vod_play_from`、`vod_play_url`。

---

### searchContent — 搜尋

```java
public String searchContent(String key, boolean quick) throws Exception

public String searchContent(String key, boolean quick, String pg) throws Exception
```

**觸發時機：** 使用者輸入關鍵字搜尋時呼叫。

| 參數      | 類型        | 說明                                     |
|---------|-----------|----------------------------------------|
| `key`   | `String`  | 搜尋關鍵字。框架會自動進行繁→簡轉換以提升相容性。              |
| `quick` | `boolean` | `true` 表示快速搜尋（只傳回基本資訊），`false` 表示完整搜尋。 |
| `pg`    | `String`  | 頁碼（僅分頁版本），從 `"1"` 開始。                  |

**回傳：** JSON 字串，結構為 [Result](#result--通用回傳物件)。

主要回傳欄位為 `list`（搜尋結果影片列表）。

> 若 `Site.quickSearch = 0`，快速搜尋會被跳過，直接回傳空結果。

---

### playerContent — 播放解析

```java
public String playerContent(String flag, String id, List<String> vipFlags) throws Exception
```

**觸發時機：** 使用者選擇集數準備播放時呼叫，需解析出實際的媒體 URL。

| 參數         | 類型             | 說明                                                     |
|------------|----------------|--------------------------------------------------------|
| `flag`     | `String`       | 播放來源名稱，對應 `vod_play_from` 中的一項（如 `"youku"`、`"iqiyi"`）。 |
| `id`       | `String`       | 集數 URL 或 ID，對應 `vod_play_url` 中某集數的 value 部分。          |
| `vipFlags` | `List<String>` | 全局 VIP 平台旗標清單，對應配置中的 `flags` 欄位（如 `["qq", "youku"]`）。  |

**回傳：** JSON 字串，結構為 [Result](#result--通用回傳物件)（播放解析結果）。

主要回傳欄位為 `url`（實際可播放的媒體 URL）。

選填欄位：

| 欄位         | 說明                                                                  |
|------------|---------------------------------------------------------------------|
| `parse`    | `0` = 直接播放，`1` = 需進一步解析（預設 `0`）。`jx=1` 效果相同。                        |
| `jx`       | 同 `parse=1`，需進一步解析。                                                 |
| `playUrl`  | 解析器前綴或指定。`json:…` 傳入 JSON 解析器，`parse:解析器名稱` 指定具名解析器，其他值作為解析 URL 前綴。 | |
| `click`    | 點擊攔截處理 URL，傳遞給解析器 WebView。                                          |
| `code`     | 非零時抑制 `msg` 顯示。                                                     |
| `header`   | 播放請求所需的 HTTP 標頭（鍵值對）。                                               |
| `flag`     | 覆蓋來源旗標，傳入 VIP 解析器時使用。                                               |
| `jxFrom`   | 強制指定解析器旗標（覆蓋 `flag` 的解析器比對結果）。                                      |
| `format`   | 媒體 MIME type（如 `"application/x-mpegURL"`），指定後播放器跳過格式自動偵測。           |
| `danmaku`  | 彈幕資料列表，詳見 [Danmaku](#danmaku--彈幕物件)。                                |
| `subs`     | 字幕列表，詳見 [Sub](#sub--字幕物件)。                                          |
| `drm`      | DRM 版權保護設定，詳見 [Drm](#drm--drm-設定物件)。                                |
| `artwork`  | 播放頁面封面圖 URL。                                                        |
| `desc`     | 播放頁面描述文字。                                                           |
| `position` | 播放恢復位置（毫秒）。                                                         |

---

### liveContent — 直播頻道列表

```java
public String liveContent(String url) throws Exception
```

**觸發時機：** 載入直播來源時呼叫，爬蟲回傳頻道列表的原始文字，框架再依格式解析（支援 TXT、M3U、JSON）。

| 參數    | 類型       | 說明                     |
|-------|----------|------------------------|
| `url` | `String` | 來源配置中的 `Live.url` 欄位值。 |

**回傳：** 頻道列表的原始文字字串（非 JSON Result），格式可為：

| 格式   | 說明                                    |
|------|---------------------------------------|
| TXT  | 每行 `頻道名稱,URL#URL2...`，以 `#genre#` 分組。 |
| M3U  | 標準 `#EXTM3U`/`#EXTINF` 格式。            |
| JSON | `Group` 物件陣列，結構與配置的 `groups` 欄位相同。    |

---

### proxy — 本地代理

```java
public Object[] proxy(Map<String, String> params) throws Exception
```

**觸發時機：** 應用程式內建本地 HTTP 代理伺服器收到請求時呼叫。

| 參數       | 類型                    | 說明                                                           |
|----------|-----------------------|--------------------------------------------------------------|
| `params` | `Map<String, String>` | 代理請求參數，從本地代理 URL 的 query string 解析而來。通常含有 `do`、`url` 等自定義參數。 |

**回傳：** `Object[]`（注意：非 JSON 字串），格式為：

```
// 200 正常回應
Object[] {
  Integer    statusCode,   // HTTP 狀態碼（200）
  String     mimeType,     // Content-Type（如 "video/mp2t"）
  InputStream body         // 回應內容
}

// 302 重定向
Object[] {
  Integer              statusCode,   // 302
  String               mimeType,     // "text/plain"
  InputStream          body,         // 通常為空或提示文字
  Map<String, String>  headers       // 含 "Location" key 的重定向標頭
}
```

---

### action — 自定義動作

```java
public String action(String action) throws Exception
```

**觸發時機：** UI 層呼叫特定自定義指令時呼叫（如登入、重新整理 Token 等）。`action` 字串格式由爬蟲自行定義，框架不解析其內容。

| 參數       | 類型       | 說明                |
|----------|----------|-------------------|
| `action` | `String` | 動作指令字串，格式由爬蟲自行定義。 |

**回傳：** JSON 字串，結構為 [Result](#result--通用回傳物件)。

---

### manualVideoCheck / isVideoFormat — 影片格式判斷

```java
public boolean manualVideoCheck() throws Exception

public boolean isVideoFormat(String url) throws Exception
```

| 方法                   | 說明                                                             |
|----------------------|----------------------------------------------------------------|
| `manualVideoCheck()` | 回傳 `true` 時，框架在 WebView 中攔截 URL 後會呼叫 `isVideoFormat()` 進行人工判斷。 |
| `isVideoFormat(url)` | 判斷指定 URL 是否為有效的直接媒體 URL。回傳 `true` 表示可直接播放。                     |

| 參數（`isVideoFormat`） | 類型       | 說明        |
|---------------------|----------|-----------|
| `url`               | `String` | 待判斷的 URL。 |

---

### destroy — 銷毀

```java
public void destroy()
```

**觸發時機：** 配置重新載入或應用程式清理快取時呼叫，釋放資源（連線、執行緒等）。

**回傳：** 無（`void`）

---

## 回傳資料結構

所有方法（`proxy` 除外）的回傳值均為 JSON 字串，解析後對應以下物件。

---

### Result — 通用回傳物件

不同方法使用的欄位不同，以下按方法分組說明。

**homeContent：**

| JSON 欄位   | 類型             | 說明                                                                     |
|-----------|----------------|------------------------------------------------------------------------|
| `class`   | `array<Class>` | 分類列表。詳見 [Class](#class--分類物件)。                                         |
| `filters` | `object`       | 篩選器定義，key 為 `type_id`，value 為 `Filter` 陣列。詳見 [Filter](#filter--篩選器物件)。 |

**homeVideoContent / categoryContent / detailContent / searchContent：**

| JSON 欄位     | 類型           | 說明                                         |
|-------------|--------------|--------------------------------------------|
| `list`      | `array<Vod>` | 影片卡片列表。詳見 [Vod](#vod--影片卡片物件)。             |
| `pagecount` | `integer`    | 總頁數（`categoryContent`、`searchContent` 使用）。 |

**playerContent：**

| JSON 欄位    | 類型               | 說明                                                                                 |
|------------|------------------|------------------------------------------------------------------------------------|
| `url`      | `string`         | 實際播放媒體 URL。                                                                        |
| `parse`    | `integer`        | `0` = 直接播放，`1` = 需進一步解析（預設 `0`）。`jx=1` 效果相同。                                       |
| `jx`       | `integer`        | 同 `parse=1`，需進一步解析（兩者任一為 `1` 即觸發解析流程）。                                             |
| `playUrl`  | `string`         | 解析器前綴或指定。`json:…` 傳入 JSON 解析器，`parse:解析器名稱` 指定具名解析器，其他值作為解析 URL 前綴。                |
| `key`      | `string`         | 來源 `key`，用於從配置查找對應 `Site.click`。當爬蟲未回傳 `click` 時，框架以此 key 從 VodConfig 取得 click。    |
| `click`    | `string`         | 點擊攔截處理 URL，傳遞給解析器 WebView 執行點擊動作。                                                  |
| `code`     | `integer`        | 非零時抑制 `msg` 顯示（通常用於錯誤狀態碼）。                                                         |
| `header`   | `object`         | 播放請求的額外 HTTP 標頭，鍵值對格式。                                                             |
| `flag`     | `string`         | 播放來源旗標名稱，覆蓋原始 `flag` 參數。                                                           |
| `jxFrom`   | `string`         | 強制指定解析器旗標（覆蓋 `flag` 的解析器比對結果）。                                                     |
| `format`   | `string`         | 媒體 MIME type（如 `"application/x-mpegURL"`、`"application/dash+xml"`），指定後播放器跳過格式自動偵測。 |
| `danmaku`  | `array<Danmaku>` | 彈幕資料列表，詳見 [Danmaku](#danmaku--彈幕物件)。                                               |
| `subs`     | `array<Sub>`     | 字幕列表，詳見 [Sub](#sub--字幕物件)。                                                         |
| `drm`      | `Drm`            | DRM 版權保護設定，詳見 [Drm](#drm--drm-設定物件)。                                               |
| `artwork`  | `string`         | 播放頁面封面圖 URL。                                                                       |
| `desc`     | `string`         | 播放頁面描述文字。                                                                          |
| `position` | `long`           | 播放恢復位置（毫秒）。                                                                        |
| `lrc`      | `string`         | 歌詞 URL（音樂類來源使用）。                                                                   |

**通用欄位（所有 JSON 回傳方法）：**

| JSON 欄位 | 類型       | 說明       |
|---------|----------|----------|
| `msg`   | `string` | 錯誤或提示訊息。 |

---

### Vod — 影片卡片物件

`list` 陣列中的每個元素。

| JSON 欄位         | 類型        | 說明                                                                                            |
|-----------------|-----------|-----------------------------------------------------------------------------------------------|
| `vod_id`        | `string`  | **影片唯一 ID**，傳入 `detailContent` 的 `ids` 參數。                                                    |
| `vod_name`      | `string`  | 影片顯示名稱（支援 HTML 編碼）。                                                                           |
| `vod_pic`       | `string`  | 縮圖 URL。                                                                                       |
| `vod_remarks`   | `string`  | 備註標籤，顯示在縮圖上（如 `"更新至12集"`、`"HD"`）。                                                             |
| `type_name`     | `string`  | 所屬分類名稱（用於分類過濾）。                                                                               |
| `vod_year`      | `string`  | 年份。                                                                                           |
| `vod_area`      | `string`  | 地區。                                                                                           |
| `vod_director`  | `string`  | 導演。                                                                                           |
| `vod_actor`     | `string`  | 演員。                                                                                           |
| `vod_content`   | `string`  | 簡介/描述。                                                                                        |
| `vod_play_from` | `string`  | 播放來源名稱，多個來源以 `$$$` 分隔。                                                                        |
| `vod_play_url`  | `string`  | 播放集數 URL，格式詳見[下方說明](#播放集數格式vod_play_from--vod_play_url)。                                      |
| `vod_tag`       | `string`  | 特殊標記。`"folder"` 表示此項為資料夾，點擊後以 `vod_id` 作為 `tid` 呼叫 `categoryContent` 取得子列表。                    |
| `action`        | `string`  | 自訂動作字串，點擊時優先於資料夾行為觸發。type==3 呼叫 `Spider.action(action)`，type==4 發送 HTTP 請求，結果以 Toast 顯示。     |
| `cate`          | `Cate`    | 資料夾顯示樣式物件，包含 `land`、`circle`、`ratio` 三個子欄位（含義同下方三欄）。設定此欄位等同於 `vod_tag: "folder"`，即自動將此項視為資料夾。 |
| `land`          | `integer` | 橫向顯示旗標，覆蓋 [Class](#class--分類物件) 層級的 `land` 設定。                                                |
| `circle`        | `integer` | 圓形顯示旗標，覆蓋 [Class](#class--分類物件) 層級的 `circle` 設定。                                              |
| `ratio`         | `float`   | 卡片寬高比，覆蓋 [Class](#class--分類物件) 層級的 `ratio` 設定。                                                |
| `style`         | `Style`   | 此影片卡片的顯示樣式覆蓋，詳見 [CONFIG.md](CONFIG.md)。                                                       |

---

### Class — 分類物件

`class` 陣列中的每個元素。

| JSON 欄位     | 類型        | 說明                                                 |
|-------------|-----------|----------------------------------------------------|
| `type_id`   | `string`  | 分類唯一 ID，傳入 `categoryContent` 的 `tid` 參數。可縮寫為 `id`。 |
| `type_name` | `string`  | 分類顯示名稱。可縮寫為 `name`。                                |
| `type_flag` | `string`  | `"1"` 表示此分類為資料夾類型。                                 |
| `land`      | `integer` | 此分類下影片的橫向顯示旗標。                                     |
| `circle`    | `integer` | 此分類下影片的圓形顯示旗標。                                     |
| `ratio`     | `float`   | 此分類下影片卡片的寬高比。                                      |

---

### Filter — 篩選器物件

`filters` 為一個物件，key 為 `type_id`，value 為 `Filter` 陣列，每個 `Filter` 定義一個篩選維度。

```json
{
  "filters": {
    "1": [
      {
        "key": "area",
        "name": "地區",
        "value": [
          {"n": "全部", "v": ""},
          {"n": "大陸", "v": "大陸"},
          {"n": "美國", "v": "美國"}
        ]
      },
      {
        "key": "year",
        "name": "年份",
        "value": [
          {"n": "全部", "v": ""},
          {"n": "2024", "v": "2024"}
        ]
      }
    ]
  }
}
```

**Filter 欄位：**

| JSON 欄位 | 類型       | 說明                                              |
|---------|----------|-------------------------------------------------|
| `key`   | `string` | 篩選器 ID，作為 `categoryContent` 的 `extend` 參數的 key。 |
| `name`  | `string` | 篩選器顯示名稱。                                        |
| `init`  | `string` | 預設選中的選項 value（選填）。                              |
| `value` | `array`  | 可選項目列表，每項含 `n`（顯示名稱）與 `v`（傳入值）。                 |

使用者選擇後，`extend` 傳入格式為：

```json
{
  "area": "大陸",
  "year": "2024"
}
```

---

### Danmaku — 彈幕物件

`danmaku` 陣列中的每個元素。

| JSON 欄位 | 類型       | 說明                           |
|---------|----------|------------------------------|
| `url`   | `string` | 彈幕來源 URL（必填），支援本地路徑（`/` 開頭）。 |
| `name`  | `string` | 顯示名稱（選填），省略時使用 `url`。        |

---

### Sub — 字幕物件

`subs` 陣列中的每個元素。

| JSON 欄位  | 類型        | 說明                                                                                                                         |
|----------|-----------|----------------------------------------------------------------------------------------------------------------------------|
| `url`    | `string`  | 字幕檔 URL（必填）。                                                                                                               |
| `name`   | `string`  | 顯示名稱（選填）。                                                                                                                  |
| `lang`   | `string`  | 語言代碼（選填，如 `"zh-tw"`、`"en"`）。                                                                                               |
| `format` | `string`  | MIME 類型（選填），常用值：`"text/x-ssa"`、`"application/x-subrip"`。省略時框架依副檔名自動偵測。                                                     |
| `flag`   | `integer` | ExoPlayer `C.SELECTION_FLAG_*` 常數（選填）。`0` 或省略時預設為 `SELECTION_FLAG_DEFAULT`（自動選擇）；`2` = `SELECTION_FLAG_FORCED`（強制顯示，不可關閉）。 |

---

### Drm — DRM 設定物件

`drm` 物件欄位。

| JSON 欄位    | 類型        | 說明                                                     |
|------------|-----------|--------------------------------------------------------|
| `type`     | `string`  | DRM 類型：`"widevine"`、`"playready"`、`"clearkey"`。        |
| `key`      | `string`  | License Server URL（Widevine/PlayReady）或 ClearKey 金鑰字串。 |
| `header`   | `object`  | License 請求的額外 HTTP 標頭，鍵值對格式（選填）。                       |
| `forceKey` | `boolean` | `true` = 強制使用預設 License URI（選填，預設 `false`）。            |

---

### 播放集數格式（vod_play_from / vod_play_url）

`detailContent` 回傳的 `Vod` 物件中，集數資訊以特定分隔符號編碼在兩個字串欄位中。

| 符號    | 用途              |
|-------|-----------------|
| `$$$` | 分隔多個播放來源（group） |
| `#`   | 分隔同一來源下的集數      |
| `$`   | 分隔集數名稱與集數 URL   |

**範例：**

```
vod_play_from: "線路一$$$線路二"

vod_play_url:  "第01集$https://cdn1.example.com/ep1.m3u8#第02集$https://cdn1.example.com/ep2.m3u8$$$第01集$https://cdn2.example.com/ep1.m3u8#第02集$https://cdn2.example.com/ep2.m3u8"
```

對應解析結果：

```
線路一:
  - 第01集 → https://cdn1.example.com/ep1.m3u8
  - 第02集 → https://cdn1.example.com/ep2.m3u8

線路二:
  - 第01集 → https://cdn2.example.com/ep1.m3u8
  - 第02集 → https://cdn2.example.com/ep2.m3u8
```

集數 URL 的 value 部分即為 `playerContent` 的 `id` 參數。

---

## 完整 JSON 範例

### homeContent 回傳範例

```json
{
  "class": [
    {
      "type_id": "1",
      "type_name": "電影"
    },
    {
      "type_id": "2",
      "type_name": "電視劇"
    },
    {
      "type_id": "3",
      "type_name": "綜藝"
    },
    {
      "type_id": "4",
      "type_name": "動漫"
    }
  ],
  "filters": {
    "1": [
      {
        "key": "area",
        "name": "地區",
        "value": [
          {"n": "全部", "v": ""},
          {"n": "大陸", "v": "大陸"},
          {"n": "香港", "v": "香港"},
          {"n": "台灣", "v": "台灣"},
          {"n": "美國", "v": "美國"}
        ]
      },
      {
        "key": "year",
        "name": "年份",
        "value": [
          {"n": "全部", "v": ""},
          {"n": "2025", "v": "2025"},
          {"n": "2024", "v": "2024"}
        ]
      }
    ]
  }
}
```

---

### homeVideoContent / categoryContent 回傳範例

> `categoryContent` 和 `searchContent` 可額外回傳 `pagecount`；`homeVideoContent` 無此欄位。

```json
{
  "list": [
    {
      "vod_id": "12345",
      "vod_name": "範例電影",
      "vod_pic": "https://example.com/pic/12345.jpg",
      "vod_remarks": "HD",
      "type_name": "電影"
    },
    {
      "vod_id": "67890",
      "vod_name": "範例電視劇",
      "vod_pic": "https://example.com/pic/67890.jpg",
      "vod_remarks": "更新至12集",
      "type_name": "電視劇"
    }
  ],
  "pagecount": 10
}
```

---

### detailContent 回傳範例

```json
{
  "list": [
    {
      "vod_id": "12345",
      "vod_name": "範例電影",
      "vod_pic": "https://example.com/pic/12345.jpg",
      "vod_year": "2024",
      "vod_area": "大陸",
      "vod_director": "張三",
      "vod_actor": "李四, 王五",
      "vod_content": "這是一部精彩的電影...",
      "vod_remarks": "HD",
      "type_name": "電影",
      "vod_play_from": "線路一$$$線路二",
      "vod_play_url": "正片$https://cdn1.example.com/movie.m3u8$$$正片$https://cdn2.example.com/movie.m3u8"
    }
  ]
}
```

**多集電視劇範例：**

```json
{
  "list": [
    {
      "vod_id": "67890",
      "vod_name": "範例電視劇",
      "vod_play_from": "主線路$$$備用線路",
      "vod_play_url": "第01集$https://cdn1.example.com/ep1.m3u8#第02集$https://cdn1.example.com/ep2.m3u8$$$第01集$https://cdn2.example.com/ep1.m3u8#第02集$https://cdn2.example.com/ep2.m3u8"
    }
  ]
}
```

---

### playerContent 回傳範例

**直接播放（無需解析）：**

```json
{
  "parse": 0,
  "url": "https://cdn.example.com/video/ep1.m3u8",
  "header": {
    "User-Agent": "Mozilla/5.0",
    "Referer": "https://www.example.com/"
  }
}
```

**需要進一步解析（VIP 影片）：**

```json
{
  "parse": 1,
  "url": "https://www.youku.com/video/id_xxx.html",
  "flag": "youku"
}
```

**含字幕與彈幕：**

```json
{
  "parse": 0,
  "url": "https://cdn.example.com/video/ep1.m3u8",
  "header": {
    "Referer": "https://www.example.com/"
  },
  "subs": [
    {
      "name": "繁體中文",
      "url": "https://cdn.example.com/sub/ep1.zh-tw.srt",
      "lang": "zh-tw"
    },
    {
      "name": "英文",
      "url": "https://cdn.example.com/sub/ep1.en.srt",
      "lang": "en"
    }
  ],
  "danmaku": [
    {
      "url": "https://danmaku.example.com/ep1.xml"
    }
  ]
}
```

---

### searchContent 回傳範例

```json
{
  "list": [
    {
      "vod_id": "12345",
      "vod_name": "範例電影",
      "vod_pic": "https://example.com/pic/12345.jpg",
      "vod_remarks": "HD",
      "type_name": "電影"
    }
  ]
}
```

---

### liveContent 回傳範例

**TXT 格式：**

```
央視頻道,#genre#
CCTV1,http://example.com/cctv1.m3u8#http://cdn2.example.com/cctv1.m3u8
CCTV2,http://example.com/cctv2.m3u8
台灣頻道,#genre#
TVBS,http://example.com/tvbs.m3u8
```

**M3U 格式：**

```
#EXTM3U
#EXTINF:-1 tvg-name="CCTV1" group-title="央視頻道",CCTV1
http://example.com/cctv1.m3u8
```

**JSON 格式：**

```json
[
  {
    "name": "央視頻道",
    "channel": [
      {
        "name": "CCTV1",
        "urls": [
          "http://example.com/cctv1.m3u8"
        ]
      }
    ]
  }
]
```

---

## 爬蟲本地代理 URL

爬蟲可在回傳的媒體 URL 中使用 `proxy://` 協議，將請求導向本地代理伺服器，由對應語言的 `proxy()`
方法處理。這樣可以在播放器無法直接存取來源時，讓爬蟲居中轉發資料。

| 語言         | 回傳 URL 前綴        | 取得代理 URL 的方法                  |
|------------|------------------|-------------------------------|
| Java（JAR）  | `proxy://`       | `Proxy.getUrl(boolean local)` |
| Python     | `proxy://?do=py` | `getProxyUrl(boolean local)`  |
| JavaScript | `proxy://?do=js` | `getProxy(boolean local)`     |

> `local` 參數：`true` 取得本地（`127.0.0.1`）代理位址，`false` 取得可對外存取的 LAN IP 位址。

完整端點說明見 [LOCAL.md — /proxy](LOCAL.md#proxy--爬蟲代理)。

**使用範例（Java）：**

```java
@Override
public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
    String proxyUrl = Proxy.getUrl(true) + "?url=" + URLEncoder.encode(id, "UTF-8") + "&token=xxx";
    return "{\"parse\":0,\"url\":\"" + proxyUrl + "\"}";
}

@Override
public Object[] proxy(Map<String, String> params) throws Exception {
    String url = params.get("url");
    String token = params.get("token");
    InputStream stream = fetchWithAuth(url, token);
    return new Object[]{200, "video/mp2t", stream};
}
```
