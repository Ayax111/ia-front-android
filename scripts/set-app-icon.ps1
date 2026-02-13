param(
    [Parameter(Mandatory = $true)]
    [string]$SourceImagePath
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$resRoot = Join-Path $repoRoot "app\src\main\res"

if (-not (Test-Path -LiteralPath $SourceImagePath)) {
    throw "No existe la imagen: $SourceImagePath"
}

$sizes = @(
    @{ Folder = "mipmap-mdpi"; Size = 48 },
    @{ Folder = "mipmap-hdpi"; Size = 72 },
    @{ Folder = "mipmap-xhdpi"; Size = 96 },
    @{ Folder = "mipmap-xxhdpi"; Size = 144 },
    @{ Folder = "mipmap-xxxhdpi"; Size = 192 }
)

function Save-LauncherIcon {
    param(
        [System.Drawing.Image]$Image,
        [int]$TargetSize,
        [string]$OutputPath
    )

    $bmp = New-Object System.Drawing.Bitmap($TargetSize, $TargetSize)
    $gfx = [System.Drawing.Graphics]::FromImage($bmp)
    $gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $gfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $gfx.Clear([System.Drawing.Color]::Transparent)

    $srcW = [double]$Image.Width
    $srcH = [double]$Image.Height
    $scale = [Math]::Min($TargetSize / $srcW, $TargetSize / $srcH)
    $drawW = [int][Math]::Round($srcW * $scale)
    $drawH = [int][Math]::Round($srcH * $scale)
    $x = [int](($TargetSize - $drawW) / 2)
    $y = [int](($TargetSize - $drawH) / 2)

    $gfx.DrawImage($Image, $x, $y, $drawW, $drawH)
    $bmp.Save($OutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $gfx.Dispose()
    $bmp.Dispose()
}

$img = [System.Drawing.Image]::FromFile((Resolve-Path $SourceImagePath))
try {
    foreach ($item in $sizes) {
        $folderPath = Join-Path $resRoot $item.Folder
        New-Item -ItemType Directory -Force -Path $folderPath | Out-Null

        $outMain = Join-Path $folderPath "ic_launcher.png"
        $outRound = Join-Path $folderPath "ic_launcher_round.png"
        Save-LauncherIcon -Image $img -TargetSize $item.Size -OutputPath $outMain
        Save-LauncherIcon -Image $img -TargetSize $item.Size -OutputPath $outRound
    }
} finally {
    $img.Dispose()
}

$manifestPath = Join-Path $repoRoot "app\src\main\AndroidManifest.xml"
$manifest = Get-Content $manifestPath -Raw
if ($manifest -notmatch 'android:icon="@mipmap/ic_launcher"') {
    $manifest = $manifest -replace '<application', "<application`r`n        android:icon=`"@mipmap/ic_launcher`"`r`n        android:roundIcon=`"@mipmap/ic_launcher_round`""
}
Set-Content -Path $manifestPath -Value $manifest -Encoding UTF8

Write-Host "Iconos generados y manifest actualizado."
