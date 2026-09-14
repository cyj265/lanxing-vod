# 配置說明

本文件說明 Vod（點播）與 Live（直播）配置檔案的 JSON 結構與各欄位意義。

---

## 目錄

- [Vod 配置（VodConfig）](#vod-配置vodconfig)
    - [頂層欄位](#頂層欄位)
    - [sites — 點播來源](#sites--點播來源)
    - [parses — 解析規則](#parses--解析規則)
    - [lives — 直播來源](#lives--直播來源)
- [Live 配置（LiveConfig）](#live-配置liveconfig)
    - [頂層欄位](#頂層欄位-1)
    - [groups — 頻道分組](#groups--頻道分組)
    - [channel — 頻道項目](#channel--頻道項目)
- [共用欄位物件](#共用欄位物件)
    - [doh — DNS over HTTPS](#doh--dns-over-https)
    - [proxy — 代理伺服器](#proxy--代理伺服器)
    - [rules — 網路攔截規則](#rules--網路攔截規則)
    - [headers — 注入回應標頭](#headers--注入回應標頭)
    - [hosts — DNS 解析覆蓋](#hosts--dns-解析覆蓋)
    - [ads — 廣告過濾](#ads--廣告過濾)
    - [catchup — 追看/時移](#catchup--追看時移)
    - [style — 卡片樣式](#style--卡片樣式)
- [完整範例](#完整範例)

---

## Vod 配置（VodConfig）

Vod 配置為一個 JSON 物件，作為應用程式的主要配置入口。配置可透過 URL、本地路徑或直接貼入字串的方式載入。

### 頂層欄位

| 欄位          | 類型              | 說明                                                             |
|-------------|-----------------|----------------------------------------------------------------|
| `spider`    | `string`        | 全局 Spider JAR 路徑或 URL，提供給所有 `sites` 作為預設爬蟲。支援相對路徑（`./`、`../`）。 |
| `wallpaper` | `string`        | 桌布圖片或影片的路徑/URL。支援靜態圖、GIF、影片格式。                                 |
| `logo`      | `string`        | 應用程式 Logo 圖片路徑/URL。                                            |
| `notice`    | `string`        | 啟動公告文字，將顯示給使用者。                                                |
| `sites`     | `array<Site>`   | 點播來源清單。詳見 [sites](#sites--點播來源)。                               |
| `parses`    | `array<Parse>`  | 影片解析規則清單。詳見 [parses](#parses--解析規則)。                           |
| `lives`     | `array<Live>`   | 直播來源清單。詳見 [lives — 直播來源](#lives--直播來源)。                        |
| `doh`       | `array<Doh>`    | DNS over HTTPS 設定清單。詳見 [doh](#doh--dns-over-https)。            |
| `proxy`     | `array<Proxy>`  | 代理伺服器設定清單。詳見 [proxy](#proxy--代理伺服器)。                           |
| `rules`     | `array<Rule>`   | 網路攔截規則清單。詳見 [rules](#rules--網路攔截規則)。                           |
| `headers`   | `array<Header>` | 針對特定主機注入 HTTP 回應標頭。詳見 [headers](#headers--注入回應標頭)。             |
| `hosts`     | `array<string>` | DNS 解析覆蓋規則。詳見 [hosts](#hosts--dns-解析覆蓋)。                       |
| `flags`     | `array<string>` | 平台標示旗標，用於標記特殊平台處理（如 `"qq"`）。                                   |
| `ads`       | `array<string>` | 廣告域名過濾清單，符合的請求將被攔截。詳見 [ads](#ads--廣告過濾)。                       |

---

### sites — 點播來源

`sites` 為 `Site` 物件陣列，每個物件代表一個點播來源。

| 欄位            | 類型              | 說明                                        |
|---------------|-----------------|-------------------------------------------|
| `key`         | `string`        | 來源唯一識別碼，作為主鍵使用，不可重複。                      |
| `name`        | `string`        | 來源顯示名稱。                                   |
| `type`        | `integer`       | 來源類型，決定呼叫方式。詳見下表。                         |
| `api`         | `string`        | API 端點 URL 或爬蟲類別名稱（如 `csp_Push`）。         |
| `ext`         | `string`        | 額外擴充資料，傳入爬蟲 `init()` 使用。可為字串、JSON 物件或路徑。  |
| `jar`         | `string`        | 指定此來源使用的 Spider JAR 路徑/URL，覆蓋全局 `spider`。 |
| `click`       | `string`        | 點擊攔截處理 URL 或規則。                           |
| `playUrl`     | `string`        | 播放 URL 前綴或轉換規則。                           |
| `hide`        | `integer`       | `1` 表示在來源列表中隱藏此項目。                        |
| `timeout`     | `integer`       | 請求逾時秒數，覆蓋預設值。                             |
| `searchable`  | `integer`       | 搜尋開關。`0`=停用，`1`=啟用（預設）。                   |
| `changeable`  | `integer`       | 線路切換開關。`0`=停用，`1`=啟用（預設）。                 |
| `quickSearch` | `integer`       | 快速搜尋開關。`0`=停用，`1`=啟用。                     |
| `indexs`      | `integer`       | 索引模式旗標。`1` 表示此來源作為索引來源使用。                 |
| `categories`  | `array<string>` | 顯示的分類名稱白名單，僅顯示清單中的分類。                     |
| `header`      | `object`        | 此來源請求時附加的 HTTP 標頭，格式為鍵值對。                 |
| `style`       | `Style`         | 卡片顯示樣式。詳見 [style](#style--卡片樣式)。          |

**`type` 可選值：**

| `type` | `api` 格式                              | 說明                                                               |
|--------|---------------------------------------|------------------------------------------------------------------|
| `0`    | HTTP URL                              | 直接 GET 請求，回傳 XML 格式（`ac=videolist`）。                             |
| `1`    | HTTP URL                              | 直接 GET 請求，回傳 JSON 格式，額外支援 Filter 篩選參數（`f=`）。                     |
| `3`    | `csp_ClassName` / `xxx.js` / `xxx.py` | 爬蟲直接呼叫：JAR（DexClassLoader）、JavaScript（QuickJS）、Python（Chaquopy）。 |
| `4`    | HTTP URL                              | 同 `1`，`ext` 擴充參數以 Base64 編碼傳遞（`ext=`）。                           |

**範例：**

```json
{
  "key": "my_source",
  "name": "我的來源",
  "type": 3,
  "api": "csp_XYZ",
  "ext": "./ext.json",
  "searchable": 1,
  "changeable": 1,
  "timeout": 30,
  "header": {
    "User-Agent": "Mozilla/5.0"
  },
  "style": {
    "type": "rect",
    "ratio": 1.33
  }
}
```

---

### parses — 解析規則

`parses` 為 `Parse` 物件陣列，定義影片 URL 的解析處理規則。

| 欄位           | 類型              | 說明                                       |
|--------------|-----------------|------------------------------------------|
| `name`       | `string`        | 解析器顯示名稱。                                 |
| `type`       | `integer`       | 解析器類型。詳見下表。                              |
| `url`        | `string`        | 解析 API 端點 URL，通常以待解析的影片 URL 作為後綴參數。      |
| `ext`        | `Parse.Ext`     | 解析器擴充設定物件。                               |
| `ext.flag`   | `array<string>` | 適用旗標清單，標示此解析器適用的平台（如 `["qq", "iqiyi"]`）。 |
| `ext.header` | `object`        | 解析請求時附加的 HTTP 標頭，格式為鍵值對。                 |

**`parses.type` 可選值：**

| `type` | 說明                                   |
|--------|--------------------------------------|
| `0`    | 嗅探（WebView 攔截媒體 URL）                 |
| `1`    | JSON API（GET 請求，取回應的 `url` 欄位）       |
| `2`    | JSON 擴展（將所有 type=1 的解析器合併後送入 JAR 處理） |
| `3`    | JSON 聚合（將所有解析器資訊合併後送入 JAR 處理）        |
| `4`    | 超級解析（自動並行嘗試所有 type=0/1 的解析器）         |

**範例：**

```json
{
  "name": "通用解析",
  "type": 1,
  "url": "https://api.example.com/parse?url=",
  "ext": {
    "flag": [
      "qq",
      "youku"
    ],
    "header": {
      "Referer": "https://www.example.com/"
    }
  }
}
```

### lives — 直播來源

Vod 配置中的 `lives` 欄位用於指向外部直播配置或內嵌直播資訊。每個物件為一個 `Live` 來源，欄位定義與 [Live 配置頂層欄位](#頂層欄位-1) 相同。

常見用法為指定 `url` 指向外部 `live.json`，或直接內嵌 `groups` 頻道資料。

---

## Live 配置（LiveConfig）

Live 配置可以是獨立的 JSON 檔案，或內嵌於 Vod 配置的 `lives` 陣列中。

獨立的 `live.json` 頂層支援以下欄位：

| 欄位        | 類型              | 說明                                              |
|-----------|-----------------|-------------------------------------------------|
| `spider`  | `string`        | 全局 Spider JAR 路徑，提供給所有 `lives` 作為預設爬蟲。          |
| `lives`   | `array<Live>`   | 直播來源清單，欄位定義見下表。                                 |
| `proxy`   | `array<Proxy>`  | 代理設定，同 Vod 配置。詳見 [proxy](#proxy--代理伺服器)。        |
| `rules`   | `array<Rule>`   | 攔截規則，同 Vod 配置。詳見 [rules](#rules--網路攔截規則)。       |
| `headers` | `array<Header>` | 注入標頭，同 Vod 配置。詳見 [headers](#headers--注入回應標頭)。   |
| `hosts`   | `array<string>` | DNS 覆蓋規則，同 Vod 配置。詳見 [hosts](#hosts--dns-解析覆蓋)。 |
| `ads`     | `array<string>` | 廣告過濾清單，同 Vod 配置。詳見 [ads](#ads--廣告過濾)。           |

### 頂層欄位

`lives` 陣列中每個 `Live` 物件的欄位：

| 欄位         | 類型             | 說明                                                                                                  |
|------------|----------------|-----------------------------------------------------------------------------------------------------|
| `name`     | `string`       | 直播來源名稱，作為主鍵使用，不可重複。                                                                                 |
| `url`      | `string`       | 外部直播列表 URL（M3U、TXT 或 JSON 格式）。與 `groups` 二擇一。                                                       |
| `api`      | `string`       | 直播 API 端點或爬蟲類別名稱。                                                                                   |
| `ext`      | `string`       | 傳入直播爬蟲的額外擴充資料。                                                                                      |
| `jar`      | `string`       | 此直播來源使用的 Spider JAR 路徑/URL。                                                                         |
| `click`    | `string`       | 點擊攔截處理 URL 或規則。                                                                                     |
| `logo`     | `string`       | 頻道預設 Logo 圖片 URL，支援 `{id}`、`{name}`、`{logo}` 變數替換。                                                  |
| `epg`      | `string`       | EPG 節目表 URL，多個以逗號分隔。支援 `{id}`、`{name}`、`{epg}` 變數替換；含 `xml`/`gz` 的條目作為 XMLTV 來源，含 `{` 的條目作為 API 範本。 |
| `ua`       | `string`       | 預設 User-Agent 字串。                                                                                   |
| `origin`   | `string`       | 請求 `Origin` 標頭值。                                                                                    |
| `referer`  | `string`       | 請求 `Referer` 標頭值。                                                                                   |
| `timeZone` | `string`       | 時區設定，用於 EPG 時間顯示（如 `"Asia/Taipei"`）。                                                                |
| `timeout`  | `integer`      | 請求逾時秒數。                                                                                             |
| `header`   | `object`       | 此來源請求時附加的 HTTP 標頭，格式為鍵值對。                                                                           |
| `catchup`  | `Catchup`      | 追看/時移設定，作為此來源所有頻道的預設值。詳見 [catchup](#catchup--追看時移)。                                                 |
| `groups`   | `array<Group>` | 直播頻道分組清單（內嵌方式）。詳見 [groups](#groups--頻道分組)。                                                          |
| `boot`     | `boolean`      | 是否在應用啟動時自動選中此直播來源。多個來源設定時，最後一個生效。                                                                   |
| `pass`     | `boolean`      | `true` 表示略過密碼保護，強制顯示此來源所有分組（含設有密碼的隱藏分組）。                                                            |

**範例：**

```json
{
  "name": "我的直播",
  "url": "./live.m3u",
  "epg": "https://epg.example.com/api?id={id},https://epg.example.com/xmltv.xml.gz",
  "ua": "Mozilla/5.0",
  "timeZone": "Asia/Taipei",
  "boot": true
}
```

---

### groups — 頻道分組

`groups` 為 `Group` 物件陣列，將頻道組織為分組顯示。

| 欄位        | 類型               | 說明                                      |
|-----------|------------------|-----------------------------------------|
| `name`    | `string`         | 分組顯示名稱。                                 |
| `pass`    | `string`         | 分組密碼，設定後需輸入密碼才能瀏覽此分組。                   |
| `channel` | `array<Channel>` | 此分組下的頻道清單。詳見 [channel](#channel--頻道項目)。 |

**範例：**

```json
{
  "name": "新聞台",
  "channel": [
    {
      "name": "TVBS新聞台",
      "urls": [
        "http://example.com/tvbs.m3u8"
      ]
    },
    {
      "name": "民視新聞",
      "urls": [
        "http://example.com/ftv.m3u8"
      ]
    }
  ]
}
```

---

### channel — 頻道項目

`channel` 為 `Channel` 物件陣列，每個物件代表一個直播頻道。

| 欄位        | 類型              | 說明                                                                                                                 |
|-----------|-----------------|--------------------------------------------------------------------------------------------------------------------|
| `name`    | `string`        | 頻道顯示名稱。                                                                                                            |
| `urls`    | `array<string>` | 頻道播放 URL 清單，支援多個備用線路，依序嘗試。每條 URL 可附加 `$線路名稱` 後綴指定顯示名稱（如 `"http://cdn1.example.com/hbo.m3u8$CDN1"`）；省略時自動顯示為「線路 N」。 |
| `number`  | `string`        | 頻道號碼（顯示用）。                                                                                                         |
| `logo`    | `string`        | 頻道 Logo 圖片 URL，覆蓋來源預設值。                                                                                            |
| `epg`     | `string`        | 此頻道專屬 EPG URL，覆蓋來源預設 EPG。                                                                                          |
| `ua`      | `string`        | 此頻道播放請求的 User-Agent，覆蓋來源預設值。                                                                                       |
| `click`   | `string`        | 點擊攔截處理。                                                                                                            |
| `format`  | `string`        | 指定媒體 MIME type，直接傳入播放器。常用值：`"application/x-mpegURL"`（HLS）。                                                         |
| `origin`  | `string`        | 請求 `Origin` 標頭值，覆蓋來源預設值。                                                                                           |
| `referer` | `string`        | 請求 `Referer` 標頭值，覆蓋來源預設值。                                                                                          |
| `tvgId`   | `string`        | TVG 格式 EPG 頻道 ID。                                                                                                  |
| `tvgName` | `string`        | TVG 格式 EPG 頻道名稱。                                                                                                   |
| `header`  | `object`        | 此頻道請求的額外 HTTP 標頭，格式為鍵值對。                                                                                           |
| `parse`   | `integer`       | 是否需要解析此頻道 URL。`0`=不解析，`1`=解析。                                                                                      |
| `catchup` | `Catchup`       | 此頻道的追看/時移設定，覆蓋分組及來源預設值。詳見 [catchup](#catchup--追看時移)。                                                               |
| `drm`     | `Drm`           | DRM 版權保護設定。欄位同 [playerContent 回傳的 `drm` 物件](SPIDER.md)。                                                            |

**範例：**

```json
{
  "name": "HBO",
  "urls": [
    "http://cdn1.example.com/hbo.m3u8",
    "http://cdn2.example.com/hbo.m3u8"
  ],
  "number": "601",
  "logo": "https://example.com/logo/hbo.png",
  "ua": "Mozilla/5.0",
  "header": {
    "Referer": "https://www.example.com/"
  },
  "catchup": {
    "type": "append",
    "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
  }
}
```

---

## 共用欄位物件

以下物件可在 Vod 配置或 Live 配置的對應陣列欄位中使用。

> `doh` 僅 Vod 配置支援，其餘欄位兩者均可使用。

---

### doh — DNS over HTTPS

設定加密 DNS 解析伺服器，保護 DNS 查詢隱私並防止污染。僅 Vod 配置支援。

| 欄位     | 類型              | 說明                                                    |
|--------|-----------------|-------------------------------------------------------|
| `name` | `string`        | 伺服器顯示名稱。                                              |
| `url`  | `string`        | DoH 查詢端點 URL（如 `https://dns.google/dns-query`）。       |
| `ips`  | `array<string>` | 伺服器本身的 IP 位址清單，用於 Bootstrap 解析，避免 DoH 伺服器本身需要 DNS 查詢。 |

**範例：**

```json
{
  "name": "Google DoH",
  "url": "https://dns.google/dns-query",
  "ips": [
    "8.8.4.4",
    "8.8.8.8"
  ]
}
```

---

### proxy — 代理伺服器

設定特定域名流量的代理規則。支援 HTTP、HTTPS、SOCKS4、SOCKS5 代理。

**代理 URL 格式：**

```
scheme://username:password@host:port
```

| 協議     | 範例                                  |
|--------|-------------------------------------|
| HTTP   | `http://127.0.0.1:7890`             |
| HTTPS  | `https://127.0.0.1:7890`            |
| SOCKS4 | `socks4://127.0.0.1:1080`           |
| SOCKS5 | `socks5://127.0.0.1:7891`           |
| 帶認證    | `socks5://user:pass@127.0.0.1:7891` |

| 欄位      | 類型              | 說明                              |
|---------|-----------------|---------------------------------|
| `name`  | `string`        | 代理規則顯示名稱。                       |
| `hosts` | `array<string>` | 適用此代理的主機名稱清單，支援正規表示式。靠前的規則優先匹配。 |
| `urls`  | `array<string>` | 代理伺服器 URL 清單（多個時依序嘗試）。          |

**範例：**

```json
[
  {
    "name": "指定域名代理",
    "hosts": [
      "googlevideo.com",
      "raw.githubusercontent.com"
    ],
    "urls": [
      "http://127.0.0.1:7890"
    ]
  },
  {
    "name": "全域代理",
    "hosts": [
      ".*"
    ],
    "urls": [
      "socks5://127.0.0.1:7891"
    ]
  }
]
```

---

### rules — 網路攔截規則

設定 WebView 或播放器的網路請求攔截與處理規則。

| 欄位        | 類型              | 說明                                              |
|-----------|-----------------|-------------------------------------------------|
| `name`    | `string`        | 規則顯示名稱。                                         |
| `hosts`   | `array<string>` | 觸發此規則的目標主機名稱清單。                                 |
| `regex`   | `array<string>` | 用於擷取播放 URL 的正規表示式清單，符合者視為有效的媒體 URL。             |
| `script`  | `array<string>` | 在 WebView 中執行的 JavaScript 程式碼清單，用於自動點擊、關閉廣告等操作。 |
| `exclude` | `array<string>` | 排除清單，符合此清單的 URL 不觸發 `regex` 擷取。                 |

**範例：**

```json
[
  {
    "hosts": [
      "video.example.com"
    ],
    "regex": [
      "m3u8?token="
    ],
    "exclude": [
      "preview.json"
    ]
  },
  {
    "hosts": [
      "ads.example.com"
    ],
    "script": [
      "document.querySelector('.close-btn').click()"
    ]
  }
]
```

---

### headers — 注入回應標頭

針對特定主機，在 HTTP 回應中注入自訂標頭（通常用於解除 CORS 限制）。

| 欄位       | 類型       | 說明                            |
|----------|----------|-------------------------------|
| `host`   | `string` | 目標主機名稱（不含協議，如 `example.com`）。 |
| `header` | `object` | 要注入的 HTTP 標頭物件，格式為鍵值對。        |

**範例：**

```json
{
  "host": "stream.example.com",
  "header": {
    "Access-Control-Allow-Origin": "*",
    "User-Agent": "okhttp/3.12.13"
  }
}
```

---

### hosts — DNS 解析覆蓋

覆蓋特定主機名稱的 DNS 解析結果，可用於 CDN 調度或繞過封鎖。支援萬用字元 `*`。

**格式：** `"原始主機名=目標主機名（或 IP）"`

| 欄位     | 類型       | 說明                              |
|--------|----------|---------------------------------|
| （陣列元素） | `string` | 格式為 `"原始主機名=目標主機名"`，支援萬用字元 `*`。 |

**範例：**

```json
{
  "hosts": [
    "stream.example.com=1.2.3.4",
    "old.cdn.example.com=new.cdn.example.com",
    "cache.ott.*.itv.cmvideo.cn=base-v4-free-mghy.e.cdn.chinamobile.com"
  ]
}
```

---

### ads — 廣告過濾

廣告域名黑名單，符合的 HTTP 請求將被直接攔截拒絕。

| 欄位     | 類型       | 說明      |
|--------|----------|---------|
| （陣列元素） | `string` | 要攔截的域名。 |

**範例：**

```json
{
  "ads": [
    "ads.example.com",
    "tracker.example.net"
  ]
}
```

---

### catchup — 追看/時移

設定頻道的回看/時移功能，可定義在 `Live`（來源層級）或 `Channel`（頻道層級）。

| 欄位        | 類型       | 說明                                                                                                      |
|-----------|----------|---------------------------------------------------------------------------------------------------------|
| `type`    | `string` | 時移類型。`"append"`（預設）：將格式化後的 `source` 附加至原始 URL 末尾；`"default"`：以格式化後的 `source` 完全替換原始 URL。                |
| `regex`   | `string` | 判斷此追看設定是否適用於當前 URL 的比對條件（子字串或正規表示式）。未設定時只要 `source` 非空即啟用；設定後只有 URL 符合此條件才啟用追看。                         |
| `source`  | `string` | 時移 URL 範本，**非空時才啟用追看功能**。支援 `{(b)格式}`（開始時間）、`{(e)格式}`（結束時間）、`{utc:}`（開始 Unix 秒）、`{utcend:}`（結束 Unix 秒）。 |
| `replace` | `string` | 逗號分隔的替換對（`原字串,新字串`），在組合時移 URL 前先對原始 URL 執行替換。                                                           |

**範例：**

```json
{
  "type": "append",
  "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
}
```

---

### style — 卡片樣式

設定 Vod 來源的內容卡片顯示樣式。

| 欄位      | 類型       | 說明                                                  |
|---------|----------|-----------------------------------------------------|
| `type`  | `string` | 卡片類型。可選值：`"rect"`（矩形）、`"oval"`（圓形/橢圓）、`"list"`（列表）。 |
| `ratio` | `float`  | 卡片寬高比（寬度 / 高度）。省略時使用預設比例。                           |

**`ratio` 常用值：**

| `ratio` | 比例   | 用途       |
|---------|------|----------|
| `0.75`  | 3:4  | 直式海報（預設） |
| `1`     | 1:1  | 正方形      |
| `1.33`  | 4:3  | 橫式縮圖     |
| `1.78`  | 16:9 | 寬螢幕縮圖    |

**範例：**

直式（海報，3:4）

```json
{
  "style": {
    "type": "rect"
  }
}
```

橫式（4:3）

```json
{
  "style": {
    "type": "rect",
    "ratio": 1.33
  }
}
```

正方（1:1）

```json
{
  "style": {
    "type": "rect",
    "ratio": 1
  }
}
```

正圓

```json
{
  "style": {
    "type": "oval"
  }
}
```

---

## 完整範例

### Vod 配置（config.json）

```json
{
  "spider": "./custom_spider.jar",
  "wallpaper": "./wallpaper.jpg",
  "logo": "./logo.jpg",
  "notice": "歡迎使用，本配置僅供測試！",
  "sites": [
    {
      "key": "push_agent",
      "name": "Push",
      "type": 3,
      "api": "csp_Push"
    },
    {
      "key": "my_source",
      "name": "我的影音",
      "type": 3,
      "api": "csp_MySource",
      "ext": "./myext.json",
      "searchable": 1,
      "changeable": 1,
      "timeout": 15,
      "style": {
        "type": "rect",
        "ratio": 1.33
      }
    }
  ],
  "lives": [
    {
      "name": "直播",
      "url": "./live.json",
      "epg": "https://epg.example.com/?ch={name}"
    }
  ],
  "parses": [
    {
      "name": "官方解析",
      "type": 1,
      "url": "https://api.example.com/parse?url="
    }
  ],
  "doh": [
    {
      "name": "Google",
      "url": "https://dns.google/dns-query",
      "ips": [
        "8.8.4.4",
        "8.8.8.8"
      ]
    }
  ],
  "proxy": [
    {
      "name": "指定代理",
      "hosts": [
        "googlevideo.com"
      ],
      "urls": [
        "http://127.0.0.1:7890"
      ]
    }
  ],
  "rules": [
    {
      "hosts": [
        "video.example.com"
      ],
      "regex": [
        "m3u8?token="
      ],
      "exclude": [
        "preview.json"
      ]
    }
  ],
  "headers": [
    {
      "host": "stream.example.com",
      "header": {
        "User-Agent": "okhttp/3.12.13"
      }
    }
  ],
  "hosts": [
    "old.cdn.example.com=new.cdn.example.com"
  ],
  "flags": [
    "qq"
  ],
  "ads": [
    "ads.example.com"
  ]
}
```

### Live 配置（live.json）

```json
{
  "lives": [
    {
      "name": "台灣頻道",
      "epg": "https://epg.example.com/api?id={id},https://epg.example.com/xmltv.xml.gz",
      "ua": "Mozilla/5.0",
      "timeZone": "Asia/Taipei",
      "boot": true,
      "groups": [
        {
          "name": "新聞台",
          "channel": [
            {
              "name": "TVBS新聞台",
              "number": "56",
              "urls": [
                "http://cdn1.example.com/tvbs.m3u8"
              ],
              "logo": "https://example.com/logo/tvbs.png"
            },
            {
              "name": "民視新聞",
              "number": "52",
              "urls": [
                "http://cdn1.example.com/ftv.m3u8",
                "http://cdn2.example.com/ftv.m3u8"
              ],
              "catchup": {
                "type": "append",
                "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
              }
            }
          ]
        }
      ]
    }
  ]
}
```
