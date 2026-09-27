$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$store = Join-Path (Split-Path -Parent $PSScriptRoot) 'store'
$background = [System.Drawing.ColorTranslator]::FromHtml('#101411')
$accent = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#BBF56A'))
$white = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#F0F4EC'))
$muted = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#AAB7A6'))
function Draw-Bolt($graphics, [single]$x, [single]$y, [single]$scale) {
    $points = @(@(59,26),@(33,60),@(51,60),@(47,82),@(76,45),@(57,45))
    $polygon = [System.Drawing.PointF[]]@($points | ForEach-Object { New-Object System.Drawing.PointF ($x + $_[0] * $scale),($y + $_[1] * $scale) })
    $graphics.FillPolygon($accent, $polygon)
}
$icon = New-Object System.Drawing.Bitmap 512,512
$g = [System.Drawing.Graphics]::FromImage($icon)
$g.SmoothingMode = 'AntiAlias'
$g.Clear($background)
Draw-Bolt $g 0 0 (512.0 / 108)
$icon.Save((Join-Path $store 'icon-512.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $icon.Dispose()
$feature = New-Object System.Drawing.Bitmap 1024,500
$g = [System.Drawing.Graphics]::FromImage($feature)
$g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'; $g.Clear($background)
$brandFont = New-Object System.Drawing.Font 'Segoe UI',18,([System.Drawing.FontStyle]::Bold),([System.Drawing.GraphicsUnit]::Pixel)
$titleFont = New-Object System.Drawing.Font 'Segoe UI',58,([System.Drawing.FontStyle]::Bold),([System.Drawing.GraphicsUnit]::Pixel)
$bodyFont = New-Object System.Drawing.Font 'Segoe UI',23,([System.Drawing.FontStyle]::Regular),([System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString('GIGGLETECH', $brandFont, $accent, 64, 66)
$g.DrawString("Your world.`nMore tangible.", $titleFont, $white, 60, 132)
$g.DrawString('Wi-Fi haptics. Now on Android.', $bodyFont, $muted, 64, 329)
Draw-Bolt $g 600 53 3.4
$feature.Save((Join-Path $store 'feature-graphic-1024x500.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $feature.Dispose(); $brandFont.Dispose(); $titleFont.Dispose(); $bodyFont.Dispose(); $accent.Dispose(); $white.Dispose(); $muted.Dispose()
Write-Output 'Store icon and feature graphic generated from the app bolt artwork.'
