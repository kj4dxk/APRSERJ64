param([Parameter(Mandatory)][string]$Target,[Parameter(Mandatory)][int]$LauncherId,
      [Parameter(Mandatory)][string]$Current,[Parameter(Mandatory)][string]$ReleaseApi)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$stage = $PSScriptRoot
$ready = Join-Path $stage 'ready'
$backup = Join-Path $stage 'previous.exe'
$download = Join-Path $stage 'download.exe'
$log = Join-Path (Split-Path $Target) 'logs/update.log'
$installed = $false
$closed = $false
. (Join-Path $stage 'install-verified-update.ps1')
function Notice([string]$message) {
    [Windows.Forms.MessageBox]::Show($message, 'APRSERJ64 Update') | Out-Null
}
try {
    if ($ReleaseApi -ine 'https://api.github.com/repos/kj4dxk/APRSERJ64/releases/latest') {throw 'Use the official APRSERJ64 stable-release endpoint'}
    Set-Content -LiteralPath (Join-Path $stage 'status') -Value 'Checking GitHub'
    $release = Invoke-RestMethod -Uri $ReleaseApi -TimeoutSec 30
    $version = [string]$release.tag_name
    if ($release.draft -or $release.prerelease -or $version -notmatch '^\d{4}\.\d{2}\.\d{2}\.\d{4}$') { throw 'Invalid stable release' }
    if ([string]::CompareOrdinal($Current,$version) -ge 0) {
        Notice "No newer stable release is available.`n`nInstalled: $Current`nLatest on GitHub: $version"
        return
    }
    $asset = @($release.assets | Where-Object name -eq 'APRSERJ64.exe')
    $url = "https://github.com/kj4dxk/APRSERJ64/releases/download/$version/APRSERJ64.exe"
    if ($asset.Count -ne 1 -or $asset[0].browser_download_url -cne $url -or $asset[0].digest -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Release executable or checksum missing' }
    $reply = [Windows.Forms.MessageBox]::Show("Install $version and restart APRSERJ64?`n`n$($release.body)", 'APRSERJ64 Update','YesNo')
    if ($reply -ne 'Yes') { return }
    Write-Host "Downloading APRSERJ64 $version..."
    Set-Content -LiteralPath (Join-Path $stage 'status') -Value "Downloading $version"
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $download -TimeoutSec 600
    if ((Get-FileHash -LiteralPath $download).Hash -ne $asset[0].digest.Substring(7) -or
        (Get-Item -LiteralPath $download).VersionInfo.ProductVersion -cne $version) { throw 'Downloaded executable failed verification' }
    $old = Get-Process -Id $LauncherId
    [IO.File]::WriteAllText((Join-Path $stage 'restart'),'')
    if (!$old.WaitForExit(90000)) { throw 'Timed out waiting for APRSERJ64 to close' }
    Install-VerifiedUpdate $Target $stage $version $log
} catch {
    Add-Content -LiteralPath $log -Value "$(Get-Date -Format o) $($_.Exception.Message)"
    Notice "Update failed: $($_.Exception.Message)"
} finally {
    foreach ($name in 'download.exe','restart','failed.exe','status','install-verified-update.ps1') {
        $file = Join-Path $stage $name
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    if (!(Test-Path -LiteralPath $backup)) {
        Remove-Item -LiteralPath $PSCommandPath
        [IO.Directory]::Delete($stage)
    }
}
