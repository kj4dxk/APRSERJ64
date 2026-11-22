# SPDX-License-Identifier: AGPL-3.0-only
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$image = Join-Path $repo 'build/compose/binaries/main/app/APRSIS64'
$out = Join-Path $repo 'build/portable'
if (!(Test-Path (Join-Path $image 'APRSIS64.exe'))) { throw 'Run createDistributable first.' }
New-Item -ItemType Directory -Force $out | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $out 'payload.zip'
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip }
[IO.Compression.ZipFile]::CreateFromDirectory($image, $zip, [IO.Compression.CompressionLevel]::Optimal, $false)
$hash = [Security.Cryptography.SHA256]::Create()
$inputStream = [IO.File]::OpenRead($zip)
try { $payloadId = ([BitConverter]::ToString($hash.ComputeHash($inputStream))).Replace('-','').Substring(0,24).ToLowerInvariant() }
finally { $inputStream.Dispose(); $hash.Dispose() }
$info = Join-Path $out 'PayloadInfo.cs'
Set-Content -LiteralPath $info -Value ('internal static class PayloadInfo { internal const string Id = "' + $payloadId + '"; }')
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
if (!(Test-Path $compiler)) { throw 'The Windows .NET Framework C# compiler is required.' }
& $compiler "/win32icon:$(Join-Path $repo 'assets/aprsis64.ico')" /nologo /target:winexe /platform:x64 /optimize+ /reference:System.IO.Compression.dll /reference:System.IO.Compression.FileSystem.dll /reference:System.Windows.Forms.dll "/resource:$zip,payload.zip" "/out:$out/APRSIS64.exe" (Join-Path $PSScriptRoot 'Launcher.cs') $info
if ($LASTEXITCODE -ne 0) { throw 'Portable launcher compilation failed.' }
Get-Item (Join-Path $out 'APRSIS64.exe') | Select-Object FullName,Length
