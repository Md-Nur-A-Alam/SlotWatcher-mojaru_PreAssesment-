# Slot Watcher page probe
#
# Logs in to the Mojaru Teacher Panel with YOUR login (typed here, never saved) and saves the
# pages Slot Watcher reads, so its slot reader can be checked against the real page layout.
#
# Run from PowerShell:   powershell -ExecutionPolicy Bypass -File tools\probe.ps1
# Output:                tools\probe-output\  (contains participant names and mobile numbers:
#                        keep it private and delete it when done)

$ErrorActionPreference = 'Stop'
$base = 'https://teacher.mojaru.com'
$out = Join-Path $PSScriptRoot 'probe-output'
New-Item -ItemType Directory -Force $out | Out-Null

$user = Read-Host 'Email/Mobile'
$secure = Read-Host 'Password' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
$pass = [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)

$login = Invoke-WebRequest "$base/teacher/login" -SessionVariable s -UseBasicParsing
$token = [regex]::Match($login.Content, 'name="_token" value="([^"]+)"').Groups[1].Value
if (-not $token) { throw 'No _token on the login page; the login page has changed.' }

$landing = Invoke-WebRequest "$base/teacher/post/login" -Method Post -WebSession $s -UseBasicParsing `
    -Body @{ _token = $token; email_or_mobile = $user; password = $pass }
$pass = $null
$landing.Content | Out-File (Join-Path $out 'after-login.html') -Encoding utf8
if ($landing.Content -match 'name="password"') { Write-Warning 'Still on the login page: wrong login?' }

$links = [regex]::Matches($landing.Content, 'href="([^"]*pre[-_]?assessment[^"]*)"', 'IgnoreCase') |
    ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
$links | Out-File (Join-Path $out 'links.txt') -Encoding utf8
Write-Host "Pre-assessment links found: $($links.Count)"

$i = 0
foreach ($l in $links) {
    $i++
    $page = Invoke-WebRequest $l -WebSession $s -UseBasicParsing
    $file = Join-Path $out "list-$i.html"
    $page.Content | Out-File $file -Encoding utf8
    Write-Host "Saved $l -> $file"
}
Write-Host "Done. Files are in $out"
