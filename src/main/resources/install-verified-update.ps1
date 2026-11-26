# SPDX-License-Identifier: AGPL-3.0-only
# Caller has verified the download and waited for the old launcher to release the destination.
function Install-VerifiedUpdate([string]$Target,[string]$Stage,[string]$Version,[string]$Log) {
    $backup=Join-Path $Stage 'previous.exe'
    $marker=Join-Path $Stage 'ready'
    $previousMarker=$env:APRSERJ64_UPDATE_READY
    $installed=$false
    try {
        [IO.File]::Replace((Join-Path $Stage 'download.exe'),$Target,$backup)
        $installed=$true
        $env:APRSERJ64_UPDATE_READY=$marker
        $new=Start-Process -FilePath $Target -PassThru
        $deadline=(Get-Date).AddMinutes(2)
        do {
            if((Test-Path -LiteralPath $marker) -and ([IO.File]::ReadAllText($marker) -ceq $Version)){
                Add-Content -LiteralPath $Log -Value "$(Get-Date -Format o) Confirmed startup of $Version"
                Remove-Item -LiteralPath $backup -ErrorAction SilentlyContinue
                return
            }
            if($new.HasExited){break}
            Start-Sleep -Milliseconds 250
        } while((Get-Date) -lt $deadline)
        if(!$new.HasExited){
            Notice 'Close the new APRSERJ64 window so the previous version can be restored.'
            $new.WaitForExit()
        }
        throw 'New version did not confirm startup'
    } catch {
        if($installed){[IO.File]::Replace($backup,$Target,(Join-Path $Stage 'failed.exe'))}
        $env:APRSERJ64_UPDATE_READY=$previousMarker
        Start-Process -FilePath $Target
        throw
    } finally {
        $env:APRSERJ64_UPDATE_READY=$previousMarker
        if(Test-Path -LiteralPath $marker){Remove-Item -LiteralPath $marker}
    }
}
