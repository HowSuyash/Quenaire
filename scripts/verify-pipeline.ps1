<#
.SYNOPSIS
  Proves the questionnaire pipeline works end to end, and measures what it costs.

.DESCRIPTION
  Walks the path a customer's document actually takes -- embed, store, retrieve
  by similarity, generate an answer -- against the running Postgres and Ollama,
  and reports latency per stage.

  The timings are the point as much as the pass/fail: cost and latency per
  question set the pricing floor, and that number cannot be guessed.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\verify-pipeline.ps1
#>

$ErrorActionPreference = 'Stop'

$Ollama    = 'http://localhost:11434'
$EmbedModel = 'nomic-embed-text'
$ChatModel  = 'llama3.2:3b'
$Container  = 'complyai-postgres'

function Step { param([string]$Text) Write-Host "`n$Text" -ForegroundColor Cyan }
function Pass { param([string]$Text) Write-Host "  PASS  $Text" -ForegroundColor Green }
function Fail { param([string]$Text) Write-Host "  FAIL  $Text" -ForegroundColor Red }
function Info { param([string]$Text) Write-Host "        $Text" -ForegroundColor DarkGray }

function Invoke-Sql {
    param([string]$Sql)
    # No 2>&1: PowerShell wraps a native command's stderr in ErrorRecords, so a
    # harmless psql NOTICE would abort the script under ErrorActionPreference=Stop.
    # Notices are silenced at the source instead.
    $quiet = "SET client_min_messages = warning; $Sql"
    docker exec $Container psql -U complyai -d complyai -tAq -v ON_ERROR_STOP=1 -c $quiet
}

function Get-Embedding {
    param([string]$Text)
    $body = @{ model = $EmbedModel; prompt = $Text } | ConvertTo-Json -Compress
    $res = Invoke-RestMethod -Uri "$Ollama/api/embeddings" -Method Post -Body $body `
                             -ContentType 'application/json' -TimeoutSec 120
    return $res.embedding
}

function To-VectorLiteral {
    param([double[]]$Vector)
    '[' + (($Vector | ForEach-Object { $_.ToString([System.Globalization.CultureInfo]::InvariantCulture) }) -join ',') + ']'
}

Write-Host "ComplyAI pipeline verification" -ForegroundColor Cyan
Write-Host ("=" * 64)

# --- prerequisites -----------------------------------------------------------

Step '1. Prerequisites'

try {
    $tags = Invoke-RestMethod -Uri "$Ollama/api/tags" -TimeoutSec 10
    $models = @($tags.models | ForEach-Object { $_.name })
} catch {
    Fail "Ollama unreachable at $Ollama"
    exit 1
}

if ($models -match $EmbedModel) { Pass "embedding model: $EmbedModel" }
else { Fail "missing $EmbedModel  ->  docker exec complyai-ollama ollama pull $EmbedModel"; exit 1 }

$haveChat = [bool]($models -match [regex]::Escape($ChatModel))
if ($haveChat) { Pass "generation model: $ChatModel" }
else { Info "generation model $ChatModel not pulled yet -- generation stage will be skipped" }

$dim = Invoke-Sql "SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid WHERE c.relname='document_chunks' AND a.attname='embedding';"
Pass "schema vector column: $dim"

# --- embedding ---------------------------------------------------------------

Step '2. Embedding'

$sw = [Diagnostics.Stopwatch]::StartNew()
$probe = Get-Embedding 'All customer data at rest is encrypted using AES-256 via AWS KMS.'
$sw.Stop()
$embedMs = $sw.ElapsedMilliseconds

Pass "$($probe.Count) dimensions in ${embedMs}ms"

if ($dim -notmatch "\($($probe.Count)\)") {
    Fail "schema expects $dim but the model produces $($probe.Count) -- inserts will be rejected"
    exit 1
}
Pass 'model dimension matches the schema'

# --- corpus ------------------------------------------------------------------

Step '3. Indexing a small corpus'

$corpus = @(
    'All customer data at rest is encrypted using AES-256 via AWS KMS with customer-managed keys rotated annually.',
    'Data in transit is protected with TLS 1.3, with TLS 1.2 as the minimum accepted version. HSTS is enforced.',
    'Our Recovery Point Objective is 15 minutes and our Recovery Time Objective is 1 hour, tested semi-annually.',
    'Multi-factor authentication is mandatory for all employees and every administrative account. SMS OTP is prohibited.',
    'Independent third-party penetration tests are performed annually; executive summaries are available under NDA.'
)

Invoke-Sql "DROP TABLE IF EXISTS pipeline_probe;" | Out-Null
Invoke-Sql "CREATE TABLE pipeline_probe (id serial primary key, content text, embedding vector($($probe.Count)));" | Out-Null

$indexSw = [Diagnostics.Stopwatch]::StartNew()
foreach ($passage in $corpus) {
    $vec = To-VectorLiteral (Get-Embedding $passage)
    $escaped = $passage.Replace("'", "''")
    Invoke-Sql "INSERT INTO pipeline_probe (content, embedding) VALUES ('$escaped', '$vec');" | Out-Null
}
$indexSw.Stop()

$count = (Invoke-Sql "SELECT count(*) FROM pipeline_probe;").Trim()
if ($count -eq "$($corpus.Count)") {
    Pass "$count passages embedded and stored in $($indexSw.ElapsedMilliseconds)ms"
} else {
    Fail "expected $($corpus.Count) rows, found $count"
    exit 1
}

# --- retrieval ---------------------------------------------------------------

Step '4. Retrieval by meaning'

# Deliberately worded differently from the corpus: word overlap would not find
# these, so a hit proves the vectors are doing the work.
$questions = @(
    @{ Q = 'How do you protect stored information?';        Expect = 'AES-256' },
    @{ Q = 'What are your disaster recovery targets?';      Expect = 'Recovery Point' },
    @{ Q = 'Is two-factor login required for staff?';       Expect = 'Multi-factor' }
)

$allHit = $true
$retrievalTotal = 0

foreach ($item in $questions) {
    $qSw = [Diagnostics.Stopwatch]::StartNew()
    $qVec = To-VectorLiteral (Get-Embedding $item.Q)
    $top = Invoke-Sql "SELECT content FROM pipeline_probe ORDER BY embedding <=> '$qVec' LIMIT 1;"
    $qSw.Stop()
    $retrievalTotal += $qSw.ElapsedMilliseconds

    if ($top -match [regex]::Escape($item.Expect)) {
        Pass "`"$($item.Q)`"  ->  matched  ($($qSw.ElapsedMilliseconds)ms)"
    } else {
        Fail "`"$($item.Q)`"  ->  expected '$($item.Expect)', got: $($top.Substring(0, [Math]::Min(60, $top.Length)))"
        $allHit = $false
    }
}

if (-not $allHit) { Fail 'semantic retrieval is not working'; exit 1 }

# --- generation --------------------------------------------------------------

$genMs = 0
if ($haveChat) {
    Step '5. Answer generation'

    $question = 'How is customer data encrypted at rest?'
    $qVec = To-VectorLiteral (Get-Embedding $question)
    $context = Invoke-Sql "SELECT string_agg(content, ' ') FROM (SELECT content FROM pipeline_probe ORDER BY embedding <=> '$qVec' LIMIT 3) t;"

    $prompt = @"
You are answering a security questionnaire. Use only the context below.

CONTEXT:
$context

QUESTION: $question

ANSWER (two sentences, factual):
"@

    $genSw = [Diagnostics.Stopwatch]::StartNew()
    $body = @{ model = $ChatModel; prompt = $prompt; stream = $false;
               options = @{ temperature = 0.1; num_predict = 200 } } | ConvertTo-Json -Compress -Depth 4
    $gen = Invoke-RestMethod -Uri "$Ollama/api/generate" -Method Post -Body $body `
                             -ContentType 'application/json' -TimeoutSec 600
    $genSw.Stop()
    $genMs = $genSw.ElapsedMilliseconds

    if ($gen.response -and $gen.response.Trim().Length -gt 20) {
        Pass "generated $($gen.eval_count) tokens in ${genMs}ms"
        Write-Host ""
        Write-Host "        $($gen.response.Trim())" -ForegroundColor White
    } else {
        Fail 'model returned nothing usable'
    }
}

# --- what it costs -----------------------------------------------------------

Step 'Cost per questionnaire (100 questions)'

$perQuestion = $embedMs + ($retrievalTotal / $questions.Count) + $genMs
$per100 = [math]::Round($perQuestion * 100 / 1000 / 60, 1)

Info "embedding      ~${embedMs}ms"
Info "retrieval      ~$([math]::Round($retrievalTotal / $questions.Count))ms"
if ($haveChat) { Info "generation     ~${genMs}ms" } else { Info "generation     not measured (model missing)" }
Info ""
Info "per question   ~$([math]::Round($perQuestion))ms"
Info "per 100 Qs     ~${per100} minutes of compute"

Invoke-Sql "DROP TABLE IF EXISTS pipeline_probe;" | Out-Null

Write-Host ""
Write-Host ("=" * 64)
if ($haveChat) {
    Write-Host "Pipeline verified end to end." -ForegroundColor Green
} else {
    Write-Host "Embedding and retrieval verified. Pull $ChatModel to verify generation." -ForegroundColor Yellow
}
Write-Host ""
