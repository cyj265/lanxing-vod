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
$GithubApi = "https://api.github.com/repos/$Owner/$Repo"

# 1) resolve APK path and tag
$Tag = ""
if ($ApkPath -ne "") {
    if (-not (Test-Path $ApkPath)) { Write-Host "ERROR: $ApkPath not found"; exit 1 }
    $Name = Split-Path $ApkPath -Leaf
    if ($Name -match "v(\d+\.\d+\.\d+)\.apk$") { $Tag = "v$($Matches[1])" }
    if ($Version -ne "") { $Tag = "v$Version" }
    if ($Tag -eq "") { Write-Host "ERROR: cannot detect version from filename, use -Version"; exit 1 }
} else {
    Write-Host "Fetching latest release from GitHub ..."
    if ($Version -ne "") {
        $Rel = Invoke-RestMethod -Uri "$GithubApi/releases/tags/v$Version" -Headers @{ "User-Agent" = "LanXingVod" }
    } else {
        $Rels = Invoke-RestMethod -Uri "$GithubApi/releases?per_page=10" -Headers @{ "User-Agent" = "LanXingVod" }
        $Rel = $Rels | Where-Object { $_.assets.name -match "lanxing-arm64_v8a-v.*\.apk$" } | Select-Object -First 1
    }
    if (-not $Rel) { Write-Host "ERROR: no APK release found on GitHub"; exit 1 }
    $Asset = $Rel.assets | Where-Object { $_.name -match "lanxing-arm64_v8a-v.*\.apk$" } | Select-Object -First 1
    if (-not $Asset) { Write-Host "ERROR: no APK asset in release $($Rel.tag_name)"; exit 1 }
    $Tag = $Rel.tag_name
    $ApkPath = Join-Path $env:TEMP $Asset.name
    Write-Host "Downloading $($Asset.browser_download_url) ..."
    Invoke-WebRequest -Uri $Asset.browser_download_url -OutFile $ApkPath -UseBasicParsing
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
    $Body = @{ tag_name = $Tag; target_commitish = "master"; name = "lanxing-vod $Tag"; body = "Mobile APK lanxing-arm64_v8a-v$Ver.apk"; prerelease = $false } | ConvertTo-Json -Compress
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
