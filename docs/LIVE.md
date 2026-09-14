# 直播來源格式說明

LiveParser 依內容自動偵測格式，支援三種直播來源：

| 格式                 | 判斷條件                          |
|--------------------|-------------------------------|
| [JSON](#json-格式) | 內容為 JSON（`[` 開頭）              |
| [M3U](#m3u-格式)     | 任一行含 `#EXTM3U`（且不含 `#genre#`） |
| [TXT](#txt-格式)     | 其他                            |

---

## 目錄

- [TXT 格式](#txt-格式)
- [M3U 格式](#m3u-格式)
- [JSON 格式](#json-格式)
- [頻道指令](#頻道指令)
- [DRM 宣告](#drm-宣告)
- [追看/時移](#追看時移)

---

## TXT 格式

每行以逗號 `,` 分為兩欄。含 `#genre#` 的行宣告分組，含 `://` 的行為頻道。

```
分組名稱,#genre#
頻道名稱,播放URL
```

**行的解析規則：**

| 行的形式             | 說明                              |
|------------------|---------------------------------|
| `名稱,#genre#`     | 宣告新分組，後續頻道歸入此分組                 |
| `名稱,URL`         | 頻道項目（第二欄含 `://`）                |
| `名稱_密碼,#genre#`  | 建立密碼保護的隱藏分組                     |
| 不含 `://` 的行      | 頻道[指令行](#頻道指令)，作用至下一個 `#genre#` |
| 首個頻道前無 `#genre#` | 自動建立無名預設分組                      |

**多線路備援**：以 `#` 分隔多個 URL，播放器依序嘗試：

```
CCTV1,http://cdn1.example.com/cctv1.m3u8#http://cdn2.example.com/cctv1.m3u8
```

**行內標頭**：URL 後接 `|key=value`，多個以 `&` 連接，多線路時每段各自帶標頭：

```
CCTV1,http://cdn1.example.com/cctv1.m3u8|User-Agent=okhttp#http://cdn2.example.com/cctv1.m3u8|Referer=https://example.com/
```

**範例：**

```
新聞台,#genre#
CCTV1,http://cdn1.example.com/cctv1.m3u8#http://cdn2.example.com/cctv1.m3u8
鳳凰資訊,http://example.com/phoenix.m3u8|User-Agent=Mozilla/5.0

體育台,#genre#
ua=Mozilla/5.0
referer=https://sports.example.com/
CCTV5,http://example.com/cctv5.m3u8
CCTV5+,http://example.com/cctv5plus.m3u8

電影台,#genre#
header={"Authorization":"Bearer token123"}
HBO,http://example.com/hbo.m3u8

成人_secretpass,#genre#
某頻道,http://example.com/adult.m3u8
```

---

## M3U 格式

以 `#EXTM3U` 開頭，每個頻道由 `#EXTINF:` 行與其後的 URL 行組成，中間可插入任意指令行。

```m3u
#EXTM3U [全域屬性...]
#EXTINF:-1 [頻道屬性...],頻道顯示名稱
[選用指令行...]
http://example.com/stream.m3u8[|行內標頭]
```

### `#EXTM3U` 全域屬性

| 屬性                    | 說明                                       |
|-----------------------|------------------------------------------|
| `tvg-url="…"`         | XMLTV EPG 節目表 URL（僅當 Live 配置未設定 EPG 時生效） |
| `url-tvg="…"`         | 同 `tvg-url`，備用寫法（同上條件）                   |
| `catchup="…"`         | 全域預設追看類型                                 |
| `catchup-source="…"`  | 全域追看 URL 模板                              |
| `catchup-replace="…"` | 全域追看 URL 替換字串                            |

```m3u
#EXTM3U tvg-url="https://epg.example.com/xmltv.xml" catchup="append" catchup-source="?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
```

### `#EXTINF` 頻道屬性

屬性寫在逗號前，頻道顯示名稱寫在逗號後至行尾。

| 屬性                    | 說明                 |
|-----------------------|--------------------|
| `tvg-id="…"`          | EPG 頻道 ID          |
| `tvg-name="…"`        | EPG 頻道名稱（可與顯示名稱不同） |
| `tvg-chno="…"`        | 頻道號碼               |
| `tvg-logo="…"`        | 頻道 Logo URL        |
| `group-title="…"`     | 所屬分組名稱             |
| `http-user-agent="…"` | 播放請求 User-Agent    |
| `catchup="…"`         | 此頻道追看類型，覆蓋全域設定     |
| `catchup-source="…"`  | 此頻道追看 URL 模板       |
| `catchup-replace="…"` | 此頻道追看替換字串          |

```m3u
#EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV-1" tvg-chno="1" tvg-logo="https://example.com/logo/cctv1.png" group-title="央視",CCTV-1
```

### M3U 專屬指令行

在 `#EXTINF:` 與 URL 行之間插入，作用於緊接的下一個 URL（[通用指令](#頻道指令)同樣適用）。

| 指令                             | 說明                             |
|--------------------------------|--------------------------------|
| `#EXTHTTP:{"Key":"Value"}`     | JSON 格式 HTTP 標頭                |
| `#EXTVLCOPT:http-user-agent=…` | VLC 風格 User-Agent              |
| `#EXTVLCOPT:http-referrer=…`   | VLC 風格 Referer                 |
| `#EXTVLCOPT:http-origin=…`     | VLC 風格 Origin                  |
| `#KODIPROP:…`                  | DRM 與媒體格式，詳見 [DRM 宣告](#drm-宣告) |

**行內標頭**：URL 末接 `|key=value&key2=value2`：

```m3u
http://example.com/stream.m3u8|User-Agent=Mozilla/5.0&Referer=https://example.com/
```

**範例：**

```m3u
#EXTM3U tvg-url="https://epg.example.com/xmltv.xml.gz" catchup="append" catchup-source="?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"

#EXTINF:-1 tvg-id="CCTV1" tvg-chno="1" tvg-logo="https://example.com/logo/cctv1.png" group-title="央視",CCTV-1
http://cdn1.example.com/cctv1.m3u8

#EXTINF:-1 tvg-id="CCTV5" group-title="央視",CCTV-5 體育
#EXTVLCOPT:http-user-agent=Mozilla/5.0
#EXTVLCOPT:http-referrer=https://sports.example.com/
http://cdn1.example.com/cctv5.m3u8|User-Agent=Mozilla/5.0

#EXTINF:-1 group-title="加密頻道",Premium HD
#KODIPROP:inputstream.adaptive.license_type=widevine
#KODIPROP:inputstream.adaptive.license_key=https://license.example.com/widevine|User-Agent=Mozilla/5.0
format=mpd
http://example.com/premium.mpd

#EXTINF:-1 group-title="加密頻道",PlayReady 頻道
#KODIPROP:inputstream.adaptive.drm_legacy=playready|https://license.example.com/playready
http://example.com/playready.mpd

#EXTINF:-1 group-title="一般頻道",需解析頻道
parse=1
click=https://example.com/click
http://example.com/parse-needed.m3u8

#EXTINF:-1 group-title="帶標頭頻道",自訂標頭
#EXTHTTP:{"Authorization":"Bearer mytoken","X-Custom":"value"}
http://example.com/auth-stream.m3u8
```

---

## JSON 格式

內容以 `[` 開頭時，直接反序列化為 `List<Group>`，結構與 Live 配置的 `groups` 欄位相同。

```json
[
  {
    "name": "新聞台",
    "channel": [
      {
        "name": "TVBS新聞台",
        "urls": [
          "http://cdn1.example.com/tvbs.m3u8",
          "http://cdn2.example.com/tvbs.m3u8"
        ],
        "number": "56",
        "logo": "https://example.com/logo/tvbs.png",
        "tvgId": "TVBS",
        "ua": "Mozilla/5.0",
        "catchup": {
          "type": "append",
          "source": "?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
        }
      }
    ]
  },
  {
    "name": "加密頻道",
    "pass": "secretpass",
    "channel": [
      {
        "name": "Premium",
        "urls": [
          "http://example.com/premium.mpd"
        ],
        "format": "application/dash+xml",
        "drm": {
          "type": "widevine",
          "key": "https://license.example.com/widevine",
          "header": {
            "User-Agent": "Mozilla/5.0"
          }
        }
      }
    ]
  }
]
```

> 完整欄位定義見 [CONFIG.md — channel 頻道項目](CONFIG.md#channel--頻道項目)。

---

## 頻道指令

以下指令在 **M3U** 與 **TXT** 格式中均適用，寫法完全一致。

- **TXT**：指令行寫在頻道行前，作用至下一個 `#genre#`（包含該分組內所有後續頻道及其多線路 URL）。
- **M3U**：指令行寫在 `#EXTINF:` 與 URL 行之間，僅作用於緊接的下一個 URL（每個 URL 處理後立即清除）。

| 指令          | 範例                             | 說明                                    |
|-------------|--------------------------------|---------------------------------------|
| `ua=`       | `ua=Mozilla/5.0`               | 播放請求 User-Agent                       |
| `origin=`   | `origin=https://example.com`   | 請求 Origin 標頭                          |
| `referer=`  | `referer=https://example.com/` | 請求 Referer 標頭（`referrer=` 雙 r 寫法同樣接受） |
| `header=`   | `header={"X-Token":"abc"}`     | 任意 HTTP 標頭（JSON 格式）                   |
| `format=`   | `format=mpd`                   | 強制指定媒體格式                              |
| `parse=`    | `parse=1`                      | `1` = 需透過解析器處理此 URL                   |
| `click=`    | `click=https://example.com/c`  | 點擊攔截處理 URL                            |
| `forceKey=` | `forceKey=true`                | 強制使用 DRM 金鑰                           |

**`format` 可選值：**

| 值              | 說明                                   |
|----------------|--------------------------------------|
| `hls`          | HLS 串流（`application/x-mpegURL`）      |
| `dash` 或 `mpd` | MPEG-DASH 串流（`application/dash+xml`） |

---

## DRM 宣告

僅 M3U 格式支援，透過 `#KODIPROP:` 行宣告，寫在 `#EXTINF:` 與 URL 行之間。

| 指令                                                  | 說明                                           |
|-----------------------------------------------------|----------------------------------------------|
| `#KODIPROP:inputstream.adaptive.license_type=…`     | DRM 類型：`widevine` / `playready` / `clearkey` |
| `#KODIPROP:inputstream.adaptive.license_key=…`      | DRM 授權伺服器 URL（或 ClearKey JSON）               |
| `#KODIPROP:inputstream.adaptive.drm_legacy=類型\|URL` | 快速宣告，類型與授權 URL 合一                            |
| `#KODIPROP:inputstream.adaptive.manifest_type=…`    | 媒體格式：`mpd` / `dash` / `hls`                  |
| `#KODIPROP:inputstream.adaptive.stream_headers=…`   | 串流請求標頭（`key=val&key2=val2`）                  |
| `#KODIPROP:inputstream.adaptive.common_headers=…`   | 通用請求標頭（同上格式）                                 |

**授權伺服器標頭**：附加於 `license_key` URL 後，以 `|` 分隔：

```m3u
#KODIPROP:inputstream.adaptive.license_key=https://license.example.com/widevine|User-Agent=Mozilla/5.0&token=abc
```

**標準寫法（Widevine）：**

```m3u
#EXTINF:-1 group-title="加密頻道",Premium HD
#KODIPROP:inputstream.adaptive.license_type=widevine
#KODIPROP:inputstream.adaptive.license_key=https://license.example.com/widevine|User-Agent=Mozilla/5.0
format=mpd
http://example.com/premium.mpd
```

**快速寫法（`drm_legacy`）：**

```m3u
#EXTINF:-1 group-title="加密頻道",PlayReady 頻道
#KODIPROP:inputstream.adaptive.drm_legacy=playready|https://license.example.com/playready
http://example.com/playready.mpd
```

**ClearKey（本地 JSON 金鑰）：**

```m3u
#EXTINF:-1 group-title="加密頻道",ClearKey 頻道
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key={"keys":[{"kty":"oct","k":"base64key","kid":"base64kid"}],"type":"temporary"}
http://example.com/clearkey.mpd
```

**ClearKey（簡短 hex 格式）：** 以 `kid_hex:key_hex` 表示，多對以逗號分隔，框架自動轉換為標準 JSON：

```m3u
#KODIPROP:inputstream.adaptive.license_type=clearkey
#KODIPROP:inputstream.adaptive.license_key=a7e61c373e219033c21091c302f996c5:100b6c20940f779a4589152b57d2dacb
http://example.com/clearkey.mpd
```

**`stream_headers` 也支援 `drmScheme` / `drmLicense` 鍵名：**

```m3u
#KODIPROP:inputstream.adaptive.stream_headers=drmScheme=widevine&drmLicense=https://license.example.com/
```

---

## 追看/時移

在 `#EXTM3U`、`#EXTINF` 或 Live 配置的 `catchup` 欄位中設定，頻道層級設定覆蓋全域。

**`type` 可選值：**

| 值         | 說明                            |
|-----------|-------------------------------|
| `append`  | 將 `source` 模板附加至原始 URL 末尾（預設） |
| `default` | 以格式化後的 `source` 完全替換原始 URL    |

**`source` 模板變數：**

| 變數                     | 說明               | 輸出範例             |
|------------------------|------------------|------------------|
| `${(b)yyyyMMddHHmmss}` | 節目開始時間           | `20260312140000` |
| `${(e)yyyyMMddHHmmss}` | 節目結束時間           | `20260312150000` |
| `{utc:}`               | 節目開始 Unix 時間戳（秒） | `1741780800`     |
| `{utcend:}`            | 節目結束 Unix 時間戳（秒） | `1741784400`     |

**範例（`append` 類型）：**

```
catchup="append" catchup-source="?playseek=${(b)yyyyMMddHHmmss}-${(e)yyyyMMddHHmmss}"
```

**`catchup-replace` 用途**：

組合時移 URL 前，先對原始 URL 執行字串替換。格式為 `原字串,新字串`（逗號分隔）：

```
catchup-replace="/PLTV/,/TVOD/"
```
