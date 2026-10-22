param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '../../build/generated/mediaResources/assets/orion-client/media')
)

$ErrorActionPreference = 'Stop'
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

function Get-MediaSha256([string]$Path) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($algorithm.ComputeHash([IO.File]::ReadAllBytes($Path))).Replace('-', '').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
    }
}

Push-Location $PSScriptRoot
try {
    # global.json фиксирует компилятор; lock-файл фиксирует версии и хеши пакетов
    & dotnet restore OrionMediaHelpers.csproj --locked-mode --source https://api.nuget.org/v3/index.json
    if ($LASTEXITCODE -ne 0) { throw 'Media helper restore failed' }

    $checksums = @()
    foreach ($helperName in @('OrionThumbnail', 'OrionVolume')) {
        $first = Join-Path $PSScriptRoot "bin/verify-first/$helperName"
        $second = Join-Path $PSScriptRoot "bin/verify-second/$helperName"
        foreach ($destination in @($first, $second)) {
            & dotnet build OrionMediaHelpers.csproj -c Release --no-restore -t:Rebuild `
                "-p:HelperName=$helperName" "-p:OutputPath=$destination/" -p:AppendTargetFrameworkToOutputPath=false
            if ($LASTEXITCODE -ne 0) { throw "Build failed: $helperName" }
        }
        $executable = Join-Path $first "$helperName.exe"
        $hash = Get-MediaSha256 $executable
        $rebuiltHash = Get-MediaSha256 (Join-Path $second "$helperName.exe")
        if ($hash -ne $rebuiltHash) { throw "Non-reproducible media helper: $helperName" }

        Copy-Item -LiteralPath $executable -Destination (Join-Path $OutputDirectory "$helperName.exe") -Force
        $checksums += "$hash  $helperName.exe"
    }
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'SHA256SUMS'), ($checksums -join "`n") + "`n", [Text.UTF8Encoding]::new($false))
    Write-Host "Verified media helpers: $OutputDirectory"
    $checksums | Write-Host
} finally {
    Pop-Location
}
