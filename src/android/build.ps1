$ErrorActionPreference = "Stop"
$root = "C:\Users\Administrator\AppData\Local\AgnesDroid"
$proj = Join-Path $root "project"
$build = Join-Path $root "build"
$bt = Join-Path $root "sdk\build-tools\34.0.0"
$jar = Join-Path $root "sdk\platforms\android-33\android.jar"
$javaHome = Join-Path $root "jdk\jdk-17.0.13+11"
$env:JAVA_HOME = $javaHome
$env:PATH = (Join-Path $javaHome "bin") + ";" + $bt + ";" + $env:PATH

Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $build | Out-Null

Write-Host "[1/6] aapt2 compile + link"
$reszip = Join-Path $build "res.zip"
& (Join-Path $bt "aapt2.exe") compile --dir (Join-Path $proj "res") -o $reszip
& (Join-Path $bt "aapt2.exe") link -o (Join-Path $build "base.apk") `
    -I $jar `
    --manifest (Join-Path $proj "AndroidManifest.xml") `
    -A (Join-Path $proj "assets") `
    --auto-add-overlay `
    $reszip

Write-Host "[2/6] javac"
New-Item -ItemType Directory -Force -Path (Join-Path $build "classes") | Out-Null
& (Join-Path $javaHome "bin\javac.exe") -encoding UTF-8 -source 8 -target 8 -nowarn `
    -classpath $jar -d (Join-Path $build "classes") `
    (Join-Path $proj "src\com\agnes\video\MainActivity.java")

Write-Host "[3/6] d8"
New-Item -ItemType Directory -Force -Path (Join-Path $build "dex") | Out-Null
$cjar = Join-Path $build "classes.jar"
& (Join-Path $javaHome "bin\jar.exe") cf $cjar -C (Join-Path $build "classes") .
& (Join-Path $bt "d8.bat") --release --min-api 21 --lib $jar --output (Join-Path $build "dex") $cjar

Write-Host "[4/6] merge dex"
Copy-Item (Join-Path $build "base.apk") (Join-Path $build "unsigned.apk") -Force
$dexFile = Join-Path $build "dex\classes.dex"
$unsFile = Join-Path $build "unsigned.apk"
$pyFile = Join-Path $build "adddex.py"
$pyCode = "import zipfile" + [char]10 + "z = zipfile.ZipFile(r'$unsFile', 'a')" + [char]10 + "z.write(r'$dexFile', 'classes.dex', zipfile.ZIP_DEFLATED)" + [char]10 + "z.close()" + [char]10 + "print('dex merged')" + [char]10
[IO.File]::WriteAllText($pyFile, $pyCode, (New-Object Text.UTF8Encoding($false)))
python $pyFile
if ($LASTEXITCODE -ne 0) { throw "dex merge failed" }

Write-Host "[5/6] keystore + zipalign"
$ks = Join-Path $root "agnes.jks"
if (-not (Test-Path $ks)) {
    & (Join-Path $javaHome "bin\keytool.exe") -genkeypair -keystore $ks -alias agnes `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass <PWD_REMOVED> -keypass <PWD_REMOVED> `
        -dname "CN=Agnes Studio, O=Local, C=CN" 2>&1 | Out-Null
}
$aligned = Join-Path $build "aligned.apk"
& (Join-Path $bt "zipalign.exe") -p -f 4 (Join-Path $build "unsigned.apk") $aligned

Write-Host "[6/6] apksigner"
$out = Join-Path $build "AgnesVideo.apk"
& (Join-Path $bt "apksigner.bat") sign --ks $ks --ks-pass pass:<PWD_REMOVED> --key-pass pass:<PWD_REMOVED> `
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true `
    --out $out $aligned
& (Join-Path $bt "apksigner.bat") verify --print-certs $out | Select-Object -First 2

"--- 产物 ---"
Get-Item $out | Select-Object FullName, @{n = "MB"; e = { [math]::Round($_.Length / 1MB, 2) } }
& (Join-Path $bt "aapt.exe") dump badging $out 2>&1 | Select-Object -First 2
