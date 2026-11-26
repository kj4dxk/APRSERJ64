# SPDX-License-Identifier: AGPL-3.0-only
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../src/main/resources/install-verified-update.ps1')
$root=Join-Path ([IO.Path]::GetTempPath()) ("aprserj64-update-test-"+[guid]::NewGuid())
[void][IO.Directory]::CreateDirectory($root)
$compiler=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
function Build-Fixture([string]$Dir,[string]$Name,[string]$Action) {
    $source=Join-Path $Dir ($Name+'.cs')
    [IO.File]::WriteAllText($source,'using System;using System.IO;class Fixture {static void Main(){'+$Action+'}}')
    & $compiler /nologo /target:winexe "/out:$Dir/$Name.exe" $source
    if($LASTEXITCODE){throw 'Fixture compilation failed'}
}
Build-Fixture $root 'old' 'File.WriteAllText(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"restored"),"yes");'
Build-Fixture $root 'healthy' 'File.WriteAllText(Environment.GetEnvironmentVariable("APRSERJ64_UPDATE_READY"),"2026.11.26.0001");'
Build-Fixture $root 'unhealthy' 'Environment.Exit(1);'
foreach($scenario in 'healthy','unhealthy'){
    $dir=Join-Path $root $scenario
    $stage=Join-Path $dir 'cache/update-test'
    [void][IO.Directory]::CreateDirectory($stage)
    $target=Join-Path $dir 'APRSERJ64.exe'
    [IO.File]::Copy((Join-Path $root 'old.exe'),$target)
    [IO.File]::Copy((Join-Path $root ($scenario+'.exe')),(Join-Path $stage 'download.exe'))
    $userFile=Join-Path $dir 'settings.toml'
    [IO.File]::WriteAllText($userFile,'existing settings')
    $failure=$null
    try {Install-VerifiedUpdate $target $stage '2026.11.26.0001' (Join-Path $dir 'update.log')}
    catch {$failure=$_.Exception.Message}
    if($scenario -eq 'unhealthy'){
        if($failure -ne 'New version did not confirm startup'){throw "Expected failed startup, got $failure"}
        $until=(Get-Date).AddSeconds(5)
        while(!(Test-Path (Join-Path $dir 'restored')) -and (Get-Date) -lt $until){Start-Sleep -Milliseconds 50}
        if(!(Test-Path (Join-Path $dir 'restored'))){throw 'Previous version did not restart'}
        $expected=Join-Path $root 'old.exe'
    } else {
        if($failure){throw $failure}
        $expected=Join-Path $root 'healthy.exe'
    }
    if((Get-FileHash $target).Hash -ne (Get-FileHash $expected).Hash){throw 'Wrong executable installed'}
    if([IO.File]::ReadAllText($userFile) -ne 'existing settings'){throw 'Lost user settings'}
    Write-Output "$scenario startup test passed"
}
