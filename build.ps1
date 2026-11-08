$ErrorActionPreference = 'Stop'

$Project = Split-Path -Parent $MyInvocation.MyCommand.Path
$Javac = if ($env:JAVAC) { $env:JAVAC }
         elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\javac.exe' }
         else { (Get-Command javac.exe -ErrorAction Stop).Source }
$Jar = if ($env:JAR) { $env:JAR }
       elseif ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jar.exe' }
       else { (Get-Command jar.exe -ErrorAction Stop).Source }
$OutJar = Join-Path $Project 'build\blackout-marker-1.0.0.jar'
$DistJar = Join-Path $Project 'dist\blackout-marker-1.0.0.jar'

if (!(Test-Path -LiteralPath $Javac)) {
    throw "javac not found: $Javac (set JAVA_HOME to a JDK 25 installation)"
}
if (!(Test-Path -LiteralPath (Join-Path $Project 'libs'))) {
    throw "libs/ not found. See README.md for the local compile dependencies."
}

Push-Location $Project
try {
    Remove-Item 'build\classes' -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory 'build\classes' -Force | Out-Null

    $sources = Get-ChildItem 'src\main\java' -Recurse -Filter '*.java' |
        ForEach-Object { $_.FullName.Substring($Project.Length + 1) }
    [IO.File]::WriteAllLines((Join-Path $Project 'build\sources.txt'), [string[]]$sources, (New-Object System.Text.UTF8Encoding($false)))

    & $Javac -encoding UTF-8 --release 21 -Xlint:all -cp 'libs/*' -d 'build\classes' '@build\sources.txt'
    if ($LASTEXITCODE -ne 0) {
        throw "javac failed with exit code $LASTEXITCODE"
    }

    Copy-Item 'src\main\resources\*' 'build\classes' -Recurse -Force
    Copy-Item 'LICENSE','COPYING','NOTICE' 'build\classes' -Force

    if (Test-Path -LiteralPath $OutJar) {
        Remove-Item -LiteralPath $OutJar -Force
    }
    & $Jar --create --file $OutJar -C 'build\classes' .
    if ($LASTEXITCODE -ne 0) {
        throw "jar failed with exit code $LASTEXITCODE"
    }

    New-Item -ItemType Directory (Split-Path $DistJar) -Force | Out-Null
    Copy-Item -LiteralPath $OutJar $DistJar -Force

    Write-Host "Built $OutJar"
    Write-Host "Release artifact $DistJar"
} finally {
    Pop-Location
}
