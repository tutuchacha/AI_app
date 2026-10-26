# 冒烟：确认 /actuator/prometheus 暴露了聊天指标（不需要 Grafana）
param(
    [string]$Base = 'http://127.0.0.1:8080'
)

$text = (Invoke-WebRequest -Uri "$Base/actuator/prometheus" -UseBasicParsing).Content
$names = @(
    'ai_chat_requests_total',
    'ai_chat_tokens_total',
    'ai_chat_latency_seconds_count',
    'ai_chat_stream_active'
)

$missing = @()
foreach ($name in $names) {
    if ($text -match [regex]::Escape($name)) {
        Write-Host "[ok]   $name"
    } else {
        $missing += $name
        Write-Host "[miss] $name"
    }
}

if ($missing.Count -gt 0) {
    Write-Host "缺少指标：$($missing -join ', ')。先发一轮对话再试。" -ForegroundColor Yellow
    exit 1
}
Write-Host '聊天指标已就绪。' -ForegroundColor Green
