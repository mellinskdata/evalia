$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$mavenVersion = '3.9.16'
$mavenDir = Join-Path $PSScriptRoot "apache-maven-$mavenVersion"
$mvn = Join-Path $mavenDir 'bin\mvn.cmd'
$mavenZip = Join-Path $PSScriptRoot "apache-maven-$mavenVersion-bin.zip"
$mavenUrl = "https://dlcdn.apache.org/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
$expectedSha512 = 'ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3'
$jdkRoot = Join-Path $PSScriptRoot 'jdk-17'

Write-Host '=============================================' -ForegroundColor Cyan
Write-Host '       EVALIA 2.1 - INICIALIZADOR' -ForegroundColor Cyan
Write-Host '=============================================' -ForegroundColor Cyan

function Find-Jdk17 {
    if ($env:JAVA_HOME) {
        $candidate = $env:JAVA_HOME
        if ((Test-Path (Join-Path $candidate 'bin\java.exe')) -and (Test-Path (Join-Path $candidate 'bin\javac.exe'))) { return $candidate }
    }
    $javac = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($javac) { return (Split-Path -Parent (Split-Path -Parent $javac.Source)) }
    if (Test-Path $jdkRoot) {
        $found = Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } | Select-Object -First 1
        if ($found) { return $found.FullName }
        if (Test-Path (Join-Path $jdkRoot 'bin\javac.exe')) { return $jdkRoot }
    }
    return $null
}

$jdkHome = Find-Jdk17
if (-not $jdkHome) {
    Write-Host '[1/4] JDK 17 completo nao encontrado. Preparando Java privado do Evalia...' -ForegroundColor Cyan
    Write-Host '      Consultando o Temurin oficial (Eclipse Adoptium)...' -ForegroundColor DarkGray
    $api = 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $assets = Invoke-RestMethod -Uri $api -UseBasicParsing
    $pkg = $assets | Where-Object { $_.binary.package.link -and $_.binary.package.checksum } | Select-Object -First 1
    if (-not $pkg) { throw 'Nao foi possivel localizar o JDK 17 do Temurin.' }
    $jdkZip = Join-Path $PSScriptRoot 'temurin-jdk17.zip'
    if (Test-Path $jdkZip) { Remove-Item $jdkZip -Force }
    Write-Host '      Baixando JDK 17 (pode levar alguns minutos)...' -ForegroundColor DarkGray
    Invoke-WebRequest -UseBasicParsing -Uri $pkg.binary.package.link -OutFile $jdkZip
    $actualJdk = (Get-FileHash -Algorithm SHA256 $jdkZip).Hash.ToLowerInvariant()
    $expectedJdk = $pkg.binary.package.checksum.ToLowerInvariant()
    if ($actualJdk -ne $expectedJdk) { Remove-Item $jdkZip -Force; throw 'A verificacao SHA-256 do JDK falhou.' }
    if (Test-Path $jdkRoot) { Remove-Item $jdkRoot -Recurse -Force }
    New-Item -ItemType Directory -Path $jdkRoot | Out-Null
    Write-Host '      Extraindo JDK...' -ForegroundColor DarkGray
    Expand-Archive -Path $jdkZip -DestinationPath $jdkRoot -Force
    Remove-Item $jdkZip -Force
    $jdkHome = Find-Jdk17
    if (-not $jdkHome) { throw 'JDK foi baixado, mas javac.exe nao foi encontrado.' }
} else {
    Write-Host '[1/4] JDK completo encontrado.' -ForegroundColor Green
}
$env:JAVA_HOME = $jdkHome
$env:Path = (Join-Path $jdkHome 'bin') + ';' + $env:Path
& (Join-Path $jdkHome 'bin\javac.exe') -version

if (-not (Test-Path $mvn)) {
    Write-Host '[2/4] Preparando Maven privado do Evalia...' -ForegroundColor Cyan
    if (Test-Path $mavenZip) { Remove-Item $mavenZip -Force }
    Invoke-WebRequest -UseBasicParsing -Uri $mavenUrl -OutFile $mavenZip
    $actual = (Get-FileHash -Algorithm SHA512 $mavenZip).Hash.ToLowerInvariant()
    if ($actual -ne $expectedSha512) { Remove-Item $mavenZip -Force; throw 'A verificacao SHA-512 do Maven falhou.' }
    Expand-Archive -Path $mavenZip -DestinationPath $PSScriptRoot -Force
    Remove-Item $mavenZip -Force
} else { Write-Host '[2/4] Maven privado ja esta pronto.' -ForegroundColor Green }

Write-Host '[3/4] Baixando dependencias/compilando quando necessario...' -ForegroundColor Cyan
Write-Host '[4/4] Abrindo o Evalia...' -ForegroundColor Cyan
Push-Location $root
try {
    & $mvn '-DskipTests' 'clean' 'javafx:run'
    if ($LASTEXITCODE -ne 0) { throw "Maven terminou com codigo $LASTEXITCODE." }
} finally { Pop-Location }
