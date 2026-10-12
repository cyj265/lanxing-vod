# gitee-upload.ps1 - Upload latest lanxing-vod APK to Gitee Release (local, fast).
# Usage:
#   $env:GITEE_TOKEN = "your_gitee_private_token"
#   powershell -ExecutionPolicy Bypass -File gitee-upload.ps1              # auto: latest GitHub release
#   powershell -ExecutionPolicy Bypass -File gitee-upload.ps1 -Version 5.6.82
#   powershell -ExecutionPolicy Bypass -File gitee-upload.ps1 -ApkPath D:\path\to\app.apk
param(
    [string]$Version = "",
    [string]$ApkPath = ""
)
$ErrorActionPreference = "Stop"
$Token = $env:GITEE_TOKEN
if (-not $Token) {
    Write-Host "ERROR: env GITEE_TOKEN not set. Run:  `$env:GITEE_TOKEN = `"<your gitee token>`""
    exit 1
}
$Owner = "cyj265"
$Repo = "lanxing-vod"
$Api = "https://gitee.com/api/v5/repos/$Owner/$Repo"

# 1) resolve APK path and tag
$Tag = ""
if ($ApkPath -ne "") {
    if (-not (Test-Path $ApkPath)) { Write-Host "ERROR: $ApkPath not found"; exit 1 }
    $Name = Split-Path $ApkPath -Leaf
    if ($Name -match "v(\d+\.\d+\.\d+)\.apk$") { $Tag = "v$($Matches[1])" }
    if ($Version -ne "") { $Tag = "v$Version" }
    if ($Tag -eq "") { Write-Host "ERROR: cannot detect version from filename, use -Version"; exit 1 }
} else {
    # Do NOT call api.github.com (anonymous rate limit). Use release direct-link download.
    # Version: explicit -Version, or parse the latest tag from releases.atom (no API limit).
    if ($Version -eq "") {
        Write-Host "Fetching latest version from GitHub releases.atom ..."
        $Atom = (Invoke-WebRequest -Uri "https://github.com/$Owner/$Repo/releases.atom" -UseBasicParsing).Content
        [xml]$X = $Atom
        $Tag = $X.feed.entry[0].title
        if ($Tag -match "v(\d+\.\d+\.\d+)") { $Tag = $Matches[0] }
    } else {
        $Tag = "v$Version"
    }
    $Ver2 = $Tag.TrimStart("v")
    $Name = "lanxing-arm64_v8a-v$Ver2.apk"
    $Url = "https://github.com/$Owner/$Repo/releases/download/$Tag/$Name"
    $ApkPath = Join-Path $env:TEMP $Name
    Write-Host "Downloading $Url ..."
    Invoke-WebRequest -Uri $Url -OutFile $ApkPath -UseBasicParsing
}
$Ver = $Tag.TrimStart("v")
$Name = Split-Path $ApkPath -Leaf
$Size = [math]::Round((Get-Item $ApkPath).Length / 1MB, 1)
Write-Host "APK: $Name ($Size MB) tag=$Tag"

# 2) find or create Gitee release
Write-Host "Checking Gitee release $Tag ..."
$List = curl.exe -sS --max-time 60 "$Api/releases?per_page=100&access_token=$Token"
$RelId = ($List | ConvertFrom-Json | Where-Object { $_.tag_name -eq $Tag } | Select-Object -First 1).id
if (-not $RelId) {
    Write-Host "Creating Gitee release $Tag ..."
    $Body = @{ tag_name = $Tag; target_commitish = "master"; name = "揽星影视 $Tag"; body = "**手机版** ``lanxing-arm64_v8a-v$Ver.apk```n`n系统要求：Android 7.0（API 24）及以上。5.4.x 检查更新可直接覆盖安装。`n`n更新内容详见 GitHub 仓库提交记录。"; prerelease = $false } | ConvertTo-Json -Compress
    $Resp = curl.exe -sS --max-time 60 -X POST "$Api/releases?access_token=$Token" -H "Content-Type: application/json" -d $Body
    $RelId = ($Resp | ConvertFrom-Json).id
}
if (-not $RelId) { Write-Host "ERROR: failed to create Gitee release"; exit 1 }
Write-Host "Gitee release id=$RelId"

# 3) skip if attachment already exists
$Atts = curl.exe -sS --max-time 60 "$Api/releases/$RelId/attach_files?access_token=$Token"
$Has = $Atts | ConvertFrom-Json | Where-Object { $_.name -eq $Name }
if ($Has) {
    Write-Host "Attachment already exists, nothing to do."
    exit 0
}

# 4) upload (multipart via curl.exe)
Write-Host "Uploading $Name to Gitee (local upload, usually seconds) ..."
$Code = curl.exe -sS --max-time 600 --connect-timeout 30 -o "$env:TEMP\gitee-upload.resp" -w "%{http_code}" -X POST "$Api/releases/$RelId/attach_files?access_token=$Token" -F "file=@$ApkPath"
Write-Host "Upload HTTP=$Code"
if ($Code -eq "200" -or $Code -eq "201") {
    Write-Host "OK: https://gitee.com/$Owner/$Repo/releases/download/$Tag/$Name"
} else {
    Write-Host "ERROR: upload failed. Response:"
    Get-Content "$env:TEMP\gitee-upload.resp" -Raw -ErrorAction SilentlyContinue
    exit 1
}
