# 本地 HTTP API

應用程式啟動後會在本地綁定一個 HTTP 伺服器，埠號從 **9978** 開始依序嘗試至 **9998**，取得第一個可用埠。

```
http://127.0.0.1:{port}
```

> 實際埠號依系統可用情況而定，預設起始為 9978。

所有端點支援 GET 與 POST（除特別標注外），參數可放在 Query String 中。回應若無特別說明皆為 `text/plain`，成功回傳 `OK`，失敗回傳 `500` 與錯誤訊息。

---

## 目錄

- [/action — 動作指令](#action--動作指令)
    - [do=control — 播放控制](#docontrol--播放控制)
    - [do=refresh — 刷新指令](#dorefresh--刷新指令)
    - [do=push — 推送播放](#dopush--推送播放)
    - [do=file — 開啟檔案](#dofile--開啟檔案)
    - [do=search — 觸發搜尋](#dosearch--觸發搜尋)
    - [do=setting — 載入配置](#dosetting--載入配置)
    - [do=cast — 投放媒體](#docast--投放媒體)
    - [do=sync — 同步資料](#dosync--同步資料)
- [/cache — 快取操作](#cache--快取操作)
- [/media — 播放狀態](#media--播放狀態)
- [/file — 本地檔案系統](#file--本地檔案系統)
- [/upload — 上傳檔案](#upload--上傳檔案)
- [/newFolder — 新增資料夾](#newfolder--新增資料夾)
- [/delFolder — 刪除資料夾](#delfolder--刪除資料夾)
- [/delFile — 刪除檔案](#delfile--刪除檔案)
- [/parse — 解析頁面](#parse--解析頁面)
- [/proxy — 爬蟲代理](#proxy--爬蟲代理)
- [/device — 裝置資訊](#device--裝置資訊)
- [端點總覽](#端點總覽)

---

## /action — 動作指令

透過 `do` 參數分派不同動作。

```
GET/POST http://127.0.0.1:9978/action?do={do}&...
```

---

### do=control — 播放控制

控制目前播放器的播放狀態。

```
http://127.0.0.1:9978/action?do=control&type={type}
```

| 參數     | 說明        |
|--------|-----------|
| `type` | 控制指令，見下表。 |

**`type` 可選值：**

| `type`   | 說明       |
|----------|----------|
| `play`   | 播放       |
| `pause`  | 暫停       |
| `stop`   | 停止       |
| `replay` | 重新播放     |
| `prev`   | 上一集      |
| `next`   | 下一集      |
| `loop`   | 切換循環播放模式 |

**範例：**

```
http://127.0.0.1:9978/action?do=control&type=play
http://127.0.0.1:9978/action?do=control&type=pause
http://127.0.0.1:9978/action?do=control&type=next
```

---

### do=refresh — 刷新指令

觸發應用程式重新載入指定頁面資料，或推送內容至播放器。

```
http://127.0.0.1:9978/action?do=refresh&type={type}&...
```

**`type` 可選值：**

| `type`     | 額外參數   | 說明                                |
|------------|--------|-----------------------------------|
| `live`     | —      | 重新整理直播頁面。                         |
| `detail`   | —      | 重新整理影片詳情頁。                        |
| `player`   | —      | 重新整理播放頁面。                         |
| `subtitle` | `path` | 推送字幕至目前播放器，`path` 為字幕檔 URL。       |
| `danmaku`  | `path` | 推送彈幕至目前播放器，`path` 為彈幕檔 URL。       |
| `vod`      | `json` | 推送 Vod 物件更新，`json` 為 Vod JSON 字串。 |

**範例：**

```
http://127.0.0.1:9978/action?do=refresh&type=detail
http://127.0.0.1:9978/action?do=refresh&type=subtitle&path=http://example.com/sub.srt
http://127.0.0.1:9978/action?do=refresh&type=danmaku&path=http://example.com/danmaku.xml
```

---

### do=push — 推送播放

推送一個 URL 至應用程式進行播放。

```
http://127.0.0.1:9978/action?do=push&url={url}
```

| 參數    | 說明                    |
|-------|-----------------------|
| `url` | 要播放的媒體 URL（需 URL 編碼）。 |

**範例：**

```
http://127.0.0.1:9978/action?do=push&url=http%3A%2F%2Fexample.com%2Fvideo.m3u8
```

---

### do=file — 開啟檔案

指定本地檔案路徑，依副檔名執行對應動作。

```
http://127.0.0.1:9978/action?do=file&path={path}
```

| 參數     | 說明         |
|--------|------------|
| `path` | 本地檔案的絕對路徑。 |

**依副檔名的行為：**

| 副檔名                  | 行為            |
|----------------------|---------------|
| `.apk`               | 觸發 APK 安裝流程。  |
| `.srt` `.ssa` `.ass` | 注入字幕至目前播放器。   |
| 其他                   | 觸發設定頁面開啟對應檔案。 |

---

### do=search — 觸發搜尋

在應用程式介面觸發關鍵字搜尋。

```
http://127.0.0.1:9978/action?do=search&word={word}
```

| 參數     | 說明     |
|--------|--------|
| `word` | 搜尋關鍵字。 |

**範例：**

```
http://127.0.0.1:9978/action?do=search&word=%E9%A3%9F%E7%A5%9E
```

---

### do=setting — 載入配置

載入配置內容或指定名稱的配置。

```
http://127.0.0.1:9978/action?do=setting&text={text}&name={name}
```

| 參數     | 說明             |
|--------|----------------|
| `text` | 配置內容字串或配置 URL。 |
| `name` | 配置顯示名稱（選填）。    |

---

### do=cast — 投放媒體

將指定媒體投放至遠端裝置播放。

```
http://127.0.0.1:9978/action?do=cast&config={config}&device={device}&history={history}
```

| 參數        | 說明                                 |
|-----------|------------------------------------|
| `config`  | Config 物件的 JSON 字串，指定要投放的配置。       |
| `device`  | 目標裝置的 Device 物件 JSON 字串（含 IP 等資訊）。 |
| `history` | History 物件的 JSON 字串，包含播放歷史。        |

---

### do=sync — 同步資料

在多個裝置間同步觀看紀錄（`history`）或收藏清單（`keep`）。

```
POST http://127.0.0.1:9978/action?do=sync&type={type}&device={device}&force={force}&mode={mode}
```

**Query 參數：**

| 參數       | 說明                                         |
|----------|--------------------------------------------|
| `type`   | 同步類型：`"history"`（觀看紀錄）或 `"keep"`（收藏）。      |
| `device` | 目標裝置的 Device 物件 JSON 字串。                   |
| `force`  | `"true"` = 先刪除後合併；其他 = 直接合併。               |
| `mode`   | `"0"` = 雙向（發送+接收）；`"1"` = 僅接收；`"2"` = 僅發送。 |
| `config` | （`history` 用）Config 物件 JSON 字串。            |

**POST Body（`application/x-www-form-urlencoded`）：**

| 參數        | 說明                                |
|-----------|-----------------------------------|
| `targets` | History 或 Keep 物件陣列的 JSON 字串。     |
| `configs` | （`keep` 用）Config URL 陣列的 JSON 字串。 |

---

## /cache — 快取操作

存取應用程式的鍵值快取（基於 SharedPreferences），可用於爬蟲在不同請求間共享資料。

```
GET/POST http://127.0.0.1:9978/cache?do={do}&...
```

**Key 計算規則：** `"cache_" + (rule 為空 ? "" : rule + "_") + key`

---

### do=get — 讀取快取

```
http://127.0.0.1:9978/cache?do=get&key={key}&rule={rule}
```

| 參數     | 說明                      |
|--------|-------------------------|
| `key`  | 快取鍵名。                   |
| `rule` | 命名空間前綴，用於隔離不同爬蟲的快取（選填）。 |

**回應：** `200 OK`，回傳儲存的字串值（若不存在則為空字串）。

---

### do=set — 寫入快取

```
http://127.0.0.1:9978/cache?do=set&key={key}&value={value}&rule={rule}
```

| 參數      | 說明          |
|---------|-------------|
| `key`   | 快取鍵名。       |
| `value` | 要儲存的字串值。    |
| `rule`  | 命名空間前綴（選填）。 |

---

### do=del — 刪除快取

```
http://127.0.0.1:9978/cache?do=del&key={key}&rule={rule}
```

| 參數     | 說明          |
|--------|-------------|
| `key`  | 要刪除的快取鍵名。   |
| `rule` | 命名空間前綴（選填）。 |

---

## /media — 播放狀態

取得目前播放器的媒體資訊與播放狀態。

```
GET http://127.0.0.1:9978/media
```

**回應格式：** `application/json`

**播放器未啟動時：**

```json
{}
```

**播放器啟動時：**

```json
{
  "url": "https://cdn.example.com/video.m3u8",
  "state": 3,
  "speed": 1.0,
  "title": "範例電影",
  "artist": "來源名稱",
  "artwork": "https://example.com/cover.jpg",
  "duration": 7200000,
  "position": 1234567
}
```

**欄位說明：**

| 欄位         | 類型        | 說明                                                       |
|------------|-----------|----------------------------------------------------------|
| `url`      | `string`  | 目前串流 URL，無則為 `""`。                                       |
| `state`    | `integer` | PlaybackStateCompat 狀態碼。`1`=緩衝中，`2`=暫停，`3`=播放中，無則為 `-1`。 |
| `speed`    | `float`   | 播放速率（`1.0` = 正常速度），無則為 `-1`。                             |
| `title`    | `string`  | 媒體標題，無則為 `""`。                                           |
| `artist`   | `string`  | 藝術家或來源名稱，無則為 `""`。                                       |
| `artwork`  | `string`  | 封面圖 URI，無則為 `""`。                                        |
| `duration` | `long`    | 媒體總時長（毫秒），無則為 `-1`。                                      |
| `position` | `long`    | 目前播放位置（毫秒），無則為 `-1`。                                     |

---

## /file — 本地檔案系統

瀏覽或下載應用程式的本地儲存空間。

```
GET http://127.0.0.1:9978/file/{path}
```

| 參數     | 說明                      |
|--------|-------------------------|
| `path` | 相對於應用程式根目錄的路徑。省略時列出根目錄。 |

**目錄回應（JSON）：**

```json
{
  "parent": "videos",
  "files": [
    {
      "name": "movie.mp4",
      "path": "videos/movie.mp4",
      "time": "2025/03/05 12:00:00",
      "dir": 0
    },
    {
      "name": "subtitles",
      "path": "videos/subtitles",
      "time": "2025/03/05 10:00:00",
      "dir": 1
    }
  ]
}
```

**`files` 陣列欄位說明：**

| 欄位     | 類型        | 說明                               |
|--------|-----------|----------------------------------|
| `name` | `string`  | 檔案或目錄名稱。                         |
| `path` | `string`  | 相對於根目錄的路徑。                       |
| `time` | `string`  | 最後修改時間，格式 `yyyy/MM/dd HH:mm:ss`。 |
| `dir`  | `integer` | `1` = 目錄，`0` = 檔案。               |

**`parent` 欄位說明：**

| 值                  | 意義             |
|--------------------|----------------|
| `"."`              | 目前即為根目錄（無上一層）。 |
| `""`               | 上一層為根目錄。       |
| `"path/to/parent"` | 上一層目錄的相對路徑。    |

**檔案回應：** 直接串流檔案內容，支援 Range 請求（`206 Partial Content`）與 ETag 快取（`304 Not Modified`）。

---

## /upload — 上傳檔案

上傳檔案至指定目錄，`.zip` 檔案會自動解壓縮。

```
POST http://127.0.0.1:9978/upload?path={path}
Content-Type: multipart/form-data
```

| 參數     | 說明               |
|--------|------------------|
| `path` | 目標目錄，相對於應用程式根目錄。 |

| 檔案類型   | 行為                   |
|--------|----------------------|
| `.zip` | 解壓縮至 `path` 目錄。      |
| 其他     | 複製至 `path/filename`。 |

---

## /newFolder — 新增資料夾

在指定路徑下建立新目錄。

```
GET http://127.0.0.1:9978/newFolder?path={path}&name={name}
```

| 參數     | 說明                |
|--------|-------------------|
| `path` | 父目錄路徑，相對於應用程式根目錄。 |
| `name` | 要建立的資料夾名稱。        |

---

## /delFolder — 刪除資料夾

刪除指定目錄及其所有內容。

```
GET http://127.0.0.1:9978/delFolder?path={path}
```

| 參數     | 說明                   |
|--------|----------------------|
| `path` | 要刪除的目錄路徑，相對於應用程式根目錄。 |

---

## /delFile — 刪除檔案

刪除指定檔案。

```
GET http://127.0.0.1:9978/delFile?path={path}
```

| 參數     | 說明                   |
|--------|----------------------|
| `path` | 要刪除的檔案路徑，相對於應用程式根目錄。 |

---

## /parse — 解析頁面

將解析器腳本與目標 URL 嵌入 HTML 範本後回傳，通常供 WebView 內部使用。

```
GET http://127.0.0.1:9978/parse?jxs={jxs}&url={url}
```

| 參數    | 說明            |
|-------|---------------|
| `jxs` | 解析器腳本識別碼或內容。  |
| `url` | 待解析的媒體頁面 URL。 |

**回應格式：** `text/html`，回傳渲染後的 `parse.html` 頁面。

---

## /proxy — 爬蟲代理

將請求轉發至爬蟲的 `proxy()` 方法處理，供爬蟲自訂回應（如轉發串流、修改標頭等）。

```
GET/POST http://127.0.0.1:9978/proxy?...
```

所有 Query String 參數、請求標頭與 POST Body 會合併後傳入 `BaseLoader.get().proxy(params)`。回應由爬蟲 `proxy()` 決定，框架原封不動地轉發爬蟲回傳的串流與標頭。

爬蟲如何實作 `proxy()` 方法及取得代理 URL，見 [SPIDER.md — 爬蟲本地代理 URL](SPIDER.md#爬蟲本地代理-url)。

---

## /device — 裝置資訊

取得本機裝置資訊。

```
GET http://127.0.0.1:9978/device
```

**回應格式：** `text/plain`，內容為裝置資訊的 JSON 字串。

**回應欄位：**

| 欄位       | 類型        | 說明                    |
|----------|-----------|-----------------------|
| `uuid`   | `string`  | 裝置唯一識別碼（Android ID）。  |
| `name`   | `string`  | 裝置顯示名稱。               |
| `ip`     | `string`  | 裝置區域網路 IP 位址（含 port）。 |
| `type`   | `integer` | 裝置類型（0=手機, 1=電視）。     |
| `serial` | `string`  | 裝置序號。                 |
| `eth`    | `string`  | 有線網路 MAC 位址。          |
| `wlan`   | `string`  | 無線網路 MAC 位址。          |
| `time`   | `long`    | 回應時間戳（毫秒）。            |

---

## 端點總覽

| 端點                   | 方法       | 主要參數                              | 說明                                          |
|----------------------|----------|-----------------------------------|---------------------------------------------|
| `/action?do=control` | GET/POST | `type`                            | 播放控制（play/pause/stop/prev/next/loop/replay） |
| `/action?do=refresh` | GET/POST | `type`, `path`, `json`            | 刷新頁面或推送字幕/彈幕                                |
| `/action?do=push`    | GET/POST | `url`                             | 推送 URL 播放                                   |
| `/action?do=file`    | GET/POST | `path`                            | 開啟本地檔案                                      |
| `/action?do=search`  | GET/POST | `word`                            | 觸發關鍵字搜尋                                     |
| `/action?do=setting` | GET/POST | `text`, `name`                    | 載入配置                                        |
| `/action?do=cast`    | GET/POST | `config`, `device`, `history`     | 投放媒體至遠端裝置                                   |
| `/action?do=sync`    | POST     | `type`, `device`, `force`, `mode` | 多裝置資料同步                                     |
| `/cache?do=get`      | GET/POST | `key`, `rule`                     | 讀取快取值                                       |
| `/cache?do=set`      | GET/POST | `key`, `value`, `rule`            | 寫入快取值                                       |
| `/cache?do=del`      | GET/POST | `key`, `rule`                     | 刪除快取值                                       |
| `/media`             | GET      | —                                 | 取得播放狀態 JSON                                 |
| `/file/{path}`       | GET      | —                                 | 瀏覽目錄或下載檔案（支援 Range）                         |
| `/upload`            | POST     | `path`（multipart）                 | 上傳檔案（支援 .zip 解壓）                            |
| `/newFolder`         | GET      | `path`, `name`                    | 建立資料夾                                       |
| `/delFolder`         | GET      | `path`                            | 刪除資料夾                                       |
| `/delFile`           | GET      | `path`                            | 刪除檔案                                        |
| `/parse`             | GET      | `jxs`, `url`                      | 取得渲染後的解析 HTML 頁面                            |
| `/proxy`             | GET/POST | 自訂（轉發至爬蟲）                         | 爬蟲代理轉發                                      |
| `/device`            | GET      | —                                 | 取得裝置資訊                                      |
