Add-Type -AssemblyName System.Drawing

$srcPath = "D:\Software\dmr-cps-android\icon-src.png"
$res = "D:\Software\dmr-cps-android\app\src\main\res"

$src = [System.Drawing.Bitmap]::FromFile($srcPath)

# --- trim: find the bounding box of pixels that are not fully transparent ---
$minX = $src.Width; $minY = $src.Height; $maxX = -1; $maxY = -1
for ($y = 0; $y -lt $src.Height; $y++) {
    for ($x = 0; $x -lt $src.Width; $x++) {
        $p = $src.GetPixel($x, $y)
        if ($p.A -gt 8) {
            if ($x -lt $minX) { $minX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
if ($maxX -lt 0) { throw "icon is fully transparent" }
$w = $maxX - $minX + 1
$h = $maxY - $minY + 1
"trim box: x=$minX y=$minY w=$w h=$h (of $($src.Width)x$($src.Height))"

# --- square crop around the content, keeping aspect ---
$side = [Math]::Max($w, $h)
$cx = $minX + $w / 2
$cy = $minY + $h / 2
$cropX = [int]($cx - $side / 2)
$cropY = [int]($cy - $side / 2)

$trimmed = New-Object System.Drawing.Bitmap($side, $side, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$tg = [System.Drawing.Graphics]::FromImage($trimmed)
$tg.Clear([System.Drawing.Color]::Transparent)
$tg.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, $side, $side)), (New-Object System.Drawing.Rectangle($cropX, $cropY, $side, $side)), [System.Drawing.GraphicsUnit]::Pixel)
$tg.Dispose()

function Save-Icon {
    param([System.Drawing.Image]$image, [int]$size, [string]$path, [double]$fill)
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.Clear([System.Drawing.Color]::Transparent)
    $inner = [int]($size * $fill)
    $off = [int](($size - $inner) / 2)
    $g.DrawImage($image, $off, $off, $inner, $inner)
    $g.Dispose()
    New-Item -ItemType Directory -Force -Path (Split-Path $path -Parent) | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

$legacy = @{ "mdpi" = 48; "hdpi" = 72; "xhdpi" = 96; "xxhdpi" = 144; "xxxhdpi" = 192 }
foreach ($k in $legacy.Keys) {
    $s = $legacy[$k]
    Save-Icon $trimmed $s (Join-Path $res "mipmap-$k\ic_launcher.png") 0.92
    Save-Icon $trimmed $s (Join-Path $res "mipmap-$k\ic_launcher_round.png") 0.80
}

$fg = @{ "mdpi" = 108; "hdpi" = 162; "xhdpi" = 216; "xxhdpi" = 324; "xxxhdpi" = 432 }
foreach ($k in $fg.Keys) {
    $s = $fg[$k]
    Save-Icon $trimmed $s (Join-Path $res "mipmap-$k\ic_launcher_foreground.png") 0.60
}

$trimmed.Dispose()
$src.Dispose()
"icons regenerated"
