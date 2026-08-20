# Build the Maven Search IntelliJ plugin using the installed IDEA's own jars.
# No Gradle, no SDK download, no network needed.
$ErrorActionPreference = 'Stop'

$IDEA   = 'D:\ProgramInstall\IntelliJ IDEA 2026.1.3'
$JBR    = Join-Path $IDEA 'jbr'
$JDK    = 'D:\ProgramInstall\development\jdk\jdk-21.0.1'
$Root   = 'D:\DSWorkSpace\idea-maven-search'
$src    = Join-Path $Root 'src'
$out    = Join-Path $Root 'out'
$dist   = Join-Path $Root 'dist'
# NOTE: IDEA 2026.2 "Install Plugin from Disk" only loads files ending in .jar
# (a .zip goes down a different branch and the descriptor load returns null).
# A plugin zip IS a jar, so ship it as .jar.
$artifact = Join-Path $Root 'MavenSearch-1.5.10.jar'

if (Test-Path $out)  { Remove-Item $out -Recurse -Force }
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$javac = Join-Path $JBR 'bin\javac.exe'
$jarexe = Join-Path $JDK 'bin\jar.exe'
$files = @(Get-ChildItem $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })

Write-Host '== javac =='
& $javac --release 17 -encoding UTF-8 -nowarn -cp "$IDEA\lib\*" -d $out $files
if ($LASTEXITCODE -ne 0) { throw 'compile failed' }
Write-Host "compiled $($files.Count) sources"

Write-Host '== package =='
New-Item -ItemType Directory -Force -Path (Join-Path $dist 'META-INF') | Out-Null
Copy-Item (Join-Path $Root 'META-INF\plugin.xml') (Join-Path $dist 'META-INF\plugin.xml')
Copy-Item (Join-Path $out 'com') (Join-Path $dist 'com') -Recurse -Force

if (Test-Path $artifact) { Remove-Item $artifact -Force }
& $jarexe cf $artifact -C $dist .
if ($LASTEXITCODE -ne 0) { throw 'package failed' }

Write-Host "BUILD OK: $artifact ($((Get-Item $artifact).Length) bytes)"
