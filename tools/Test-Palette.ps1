<#
.SYNOPSIS
    HotbarX palette check: fails on gold, yellow, orange or bronze pixels in any PNG.

.DESCRIPTION
    Scans every PNG under -Path (see docs/SPEC.md, "Acceptance tests").

    A pixel FAILS when it is visible (alpha > 0), not near-black (HSV value >= MinValue),
    saturated (HSV saturation >= MinSaturation) and its hue is inside the warm band
    MinHue..MaxHue degrees.

    Files under textures/item/ also fail on partially transparent pixels (alpha 1-254).

    -Strict also fails every pixel whose green and blue channels differ. Every HotbarX
    palette token has G = B (neutral grays and pure red-axis colors), so this catches any
    drift toward orange or magenta, not just gold.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\Test-Palette.ps1 pack
.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools\Test-Palette.ps1 pack -Strict
#>
param(
    [string] $Path = 'pack',
    [double] $MinHue = 11,
    [double] $MaxHue = 75,
    [double] $MinSaturation = 0.20,
    [double] $MinValue = 0.12,
    [switch] $Strict
)

Add-Type -AssemblyName System.Drawing

if (-not (Test-Path $Path)) {
    Write-Host "Path not found: $Path"
    exit 2
}
$root = (Resolve-Path $Path).Path
$files = @(Get-ChildItem -Path $root -Recurse -Filter *.png)
if ($files.Count -eq 0) {
    Write-Host "No PNG files under $root"
    exit 2
}

$failedFiles = 0
foreach ($file in $files) {
    $relative = $file.FullName.Substring($root.Length).TrimStart('\', '/')
    $isItem = $file.FullName -match '[\\/]textures[\\/]item[\\/]'
    $warm = 0; $partial = 0; $offAxis = 0; $firstWarm = $null

    $bmp = [System.Drawing.Bitmap]::FromFile($file.FullName)
    try {
        for ($y = 0; $y -lt $bmp.Height; $y++) {
            for ($x = 0; $x -lt $bmp.Width; $x++) {
                $c = $bmp.GetPixel($x, $y)
                if ($c.A -eq 0) { continue }
                if ($isItem -and $c.A -lt 255) { $partial++ }
                if ($Strict -and $c.G -ne $c.B) { $offAxis++ }

                $max = [Math]::Max($c.R, [Math]::Max($c.G, $c.B))
                $min = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
                if ($max / 255 -lt $MinValue -or ($max - $min) / $max -lt $MinSaturation) { continue }
                $hue = $c.GetHue()
                if ($hue -ge $MinHue -and $hue -le $MaxHue) {
                    $warm++
                    if (-not $firstWarm) { $firstWarm = '({0},{1}) #{2:X2}{3:X2}{4:X2}' -f $x, $y, $c.R, $c.G, $c.B }
                }
            }
        }
    } finally {
        $bmp.Dispose()
    }

    $problems = @()
    if ($warm) { $problems += "$warm gold/yellow/bronze px, first at $firstWarm" }
    if ($partial) { $problems += "$partial semi-transparent px in an item texture" }
    if ($offAxis) { $problems += "$offAxis off-palette px (G != B)" }
    if ($problems.Count) {
        $failedFiles++
        Write-Host ("FAIL  {0}: {1}" -f $relative, ($problems -join '; '))
    }
}

if ($failedFiles) {
    Write-Host ("{0} of {1} PNG file(s) failed." -f $failedFiles, $files.Count)
    exit 1
}
Write-Host ("OK    {0} PNG file(s) checked, no forbidden pixels." -f $files.Count)
exit 0
