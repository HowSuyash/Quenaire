<#
.SYNOPSIS
  Checks everything ComplyAI needs to run, and says exactly what to do about
  whatever is missing.

.DESCRIPTION
  Written after an evening lost to diagnosing a Docker failure that turned out
  to be a missing WSL install three layers down. Each check reports the fix, not
  just the failure.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\doctor.ps1
#>

$ErrorActionPreference = 'Continue'
$script:Problems = @()

function Report {
    param(
        [string]$Name,
        [ValidateSet('ok', 'warn', 'fail')][string]$State,
        [string]$Detail,
        [string]$Fix
    )

    $mark = switch ($State) { 'ok' { '  OK  ' } 'warn' { ' WARN ' } 'fail' { ' FAIL ' } }
    $color = switch ($State) { 'ok' { 'Green' } 'warn' { 'Yellow' } 'fail' { 'Red' } }

    Write-Host $mark -ForegroundColor $color -NoNewline
    Write-Host " $($Name.PadRight(26)) $Detail"

    if ($State -ne 'ok' -and $Fix) {
        $script:Problems += [pscustomobject]@{ Name = $Name; Fix = $Fix; State = $State }
    }
}

function Test-Port {
    param([int]$Port)
    $null -ne (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

Write-Host ""
Write-Host "ComplyAI environment check" -ForegroundColor Cyan
Write-Host ("-" * 64)

# ---- toolchain -------------------------------------------------------------

$javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $javaExe -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $candidate) { $javaExe = $candidate }
}
if (-not $javaExe) {
    # Gradle may be using a JDK that was never added to PATH.
    $javaExe = (Get-ChildItem 'C:\Program Files\Java' -Filter 'java.exe' -Recurse -ErrorAction SilentlyContinue |
                Select-Object -First 1).FullName
}

if ($javaExe) {
    # `java -version` prints to stderr; capture both streams.
    $version = (& $javaExe -version 2>&1 | Select-Object -First 1) -replace '"', ''

    # "1.8.0_501" means 8; "21.0.1" and "26" mean themselves.
    $major = 0
    if ($version -match '(\d+)\.(\d+)\.') {
        $major = if ($matches[1] -eq '1') { [int]$matches[2] } else { [int]$matches[1] }
    } elseif ($version -match '\s(\d+)') {
        $major = [int]$matches[1]
    }

    if ($major -ge 21) {
        Report 'Java' 'ok' $version
    } else {
        Report 'Java' 'warn' "$version -- PATH java is too old (need 21+)" @'
Gradle uses its own toolchain, so the build still works. But `java -jar`
    on the built artifact will fail. Put a JDK 21+ first on PATH, or set
    JAVA_HOME to one.
'@
    }
} else {
    Report 'Java' 'fail' 'not found' 'Install JDK 21 or newer, or set JAVA_HOME.'
}

$node = & node --version 2>&1
if ($LASTEXITCODE -eq 0) {
    Report 'Node' 'ok' $node
} else {
    Report 'Node' 'fail' 'not on PATH' 'Install Node 20 or newer.'
}

# ---- WSL: the thing that actually blocks Docker on Windows -----------------

& wsl --status 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Report 'WSL2' 'ok' 'installed'
} else {
    Report 'WSL2' 'fail' 'not installed' @'
Docker Desktop needs WSL2 for Linux containers. In an ADMIN PowerShell:
      wsl --install
    Then REBOOT. This is the usual cause of "500 Internal Server Error"
    from the Docker engine on Windows.
'@
}

# ---- docker ---------------------------------------------------------------

$dockerCli = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCli) {
    Report 'Docker CLI' 'fail' 'not on PATH' 'Install Docker Desktop.'
} else {
    Report 'Docker CLI' 'ok' (& docker --version 2>&1)

    & docker ps 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Report 'Docker engine' 'ok' 'responding'
    } else {
        $desktop = Get-Process 'Docker Desktop' -ErrorAction SilentlyContinue
        $detail = if ($desktop) { 'Desktop running but engine not responding' } else { 'not running' }
        Report 'Docker engine' 'fail' $detail @'
Start Docker Desktop and wait for the whale icon to settle. If it never
    becomes ready, WSL2 is almost certainly the cause -- see the WSL2 check.
'@
    }
}

# ---- GPU ------------------------------------------------------------------

if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) {
    Report 'NVIDIA GPU' 'ok' ((& nvidia-smi --query-gpu=name --format=csv,noheader) -join ', ')
} else {
    Report 'NVIDIA GPU' 'warn' 'none detected -- Ollama will run on CPU' @'
Expected on most laptops. Use the base compose file (GPU is opt-in via
    docker-compose.gpu.yml). Prefer a small model: llama3.2:3b.
'@
}

# ---- memory ---------------------------------------------------------------

$ramGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 1)
if ($ramGb -ge 32) {
    Report 'Memory' 'ok' "$ramGb GB"
} elseif ($ramGb -ge 12) {
    Report 'Memory' 'warn' "$ramGb GB -- tight for the full stack" @'
Use docker-compose.minimal.yml (Postgres + Ollama only) and a 3B model.
    The full stack plus an 8B model will not fit comfortably.
'@
} else {
    Report 'Memory' 'fail' "$ramGb GB" 'Too little to run a local model alongside Postgres. Use a hosted model instead.'
}

# ---- services -------------------------------------------------------------

Write-Host ""
Write-Host "Services" -ForegroundColor Cyan
Write-Host ("-" * 64)

$services = @(
    @{ Name = 'Postgres';  Port = 5432;  Fix = 'docker compose -f docker-compose.minimal.yml up -d postgres' },
    @{ Name = 'Ollama';    Port = 11434; Fix = 'docker compose -f docker-compose.minimal.yml up -d ollama' },
    @{ Name = 'Keycloak';  Port = 8081;  Fix = 'docker compose up -d keycloak   (optional: auth)' },
    @{ Name = 'Backend';   Port = 8080;  Fix = '.\gradlew bootRun' },
    @{ Name = 'Frontend';  Port = 3000;  Fix = 'cd frontend; npm run dev' }
)

foreach ($s in $services) {
    if (Test-Port $s.Port) {
        Report $s.Name 'ok' "listening on $($s.Port)"
    } else {
        Report $s.Name 'warn' "nothing on port $($s.Port)" $s.Fix
    }
}

# ---- ollama models --------------------------------------------------------

if (Test-Port 11434) {
    try {
        $tags = Invoke-RestMethod -Uri 'http://localhost:11434/api/tags' -TimeoutSec 5
        $models = @($tags.models | ForEach-Object { $_.name })

        if ($models.Count -eq 0) {
            Report 'Ollama models' 'fail' 'none pulled' @'
docker exec -it complyai-ollama ollama pull llama3.2:3b
    docker exec -it complyai-ollama ollama pull nomic-embed-text
'@
        } else {
            Report 'Ollama models' 'ok' ($models -join ', ')

            if (-not ($models -match 'nomic-embed-text|mxbai-embed')) {
                Report 'Embedding model' 'fail' 'missing' @'
Retrieval cannot work without one:
    docker exec -it complyai-ollama ollama pull nomic-embed-text
'@
            }
        }
    } catch {
        Report 'Ollama models' 'warn' 'port open but API did not answer' 'Give the container a few more seconds.'
    }
}

# ---- verdict --------------------------------------------------------------

Write-Host ""
Write-Host ("-" * 64)

if ($script:Problems.Count -eq 0) {
    Write-Host "Everything checks out." -ForegroundColor Green
    Write-Host ""
    exit 0
}

$blocking = @($script:Problems | Where-Object { $_.State -eq 'fail' })

Write-Host "$($script:Problems.Count) thing(s) need attention" -ForegroundColor Yellow
if ($blocking.Count) {
    Write-Host "$($blocking.Count) of them will stop you." -ForegroundColor Red
}
Write-Host ""

foreach ($p in $script:Problems) {
    $tone = if ($p.State -eq 'fail') { 'Red' } else { 'Yellow' }
    Write-Host "$($p.Name):" -ForegroundColor $tone
    Write-Host "    $($p.Fix)"
    Write-Host ""
}

exit ([int]($blocking.Count -gt 0))
