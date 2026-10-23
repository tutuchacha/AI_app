#!/usr/bin/env pwsh
# 文档健康检查：预算 + 本地链接 + INDEX 覆盖。失败返回非 0，提交前跑一次。
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$fail = 0

& "$PSScriptRoot/doc-budget.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[doc-check] 预算检查未通过" -ForegroundColor Red
    $fail++
}

$indexText = Get-Content 'docs/INDEX.md' -Raw
$indexed = New-Object System.Collections.Generic.HashSet[string]
foreach ($m in [regex]::Matches($indexText, '`([^`]+\.md)`')) {
    [void]$indexed.Add(($m.Groups[1].Value -replace '\\', '/'))
}

$docs = Get-ChildItem 'docs' -Recurse -Filter *.md |
    Where-Object { $_.FullName -notmatch '\\progress\\' -and $_.Name -ne 'INDEX.md' }

foreach ($doc in $docs) {
    $rel = $doc.FullName.Substring($root.Length + 1).Replace('\', '/')
    $short = $rel -replace '^docs/', ''

    $listed = $false
    foreach ($entry in $indexed) {
        if ($short -eq $entry -or $doc.Name -eq (Split-Path $entry -Leaf)) { $listed = $true; break }
    }
    if (-not $listed) {
        Write-Host "[doc-check] UNINDEXED $rel" -ForegroundColor Yellow
        $fail++
    }

    $text = Get-Content $doc.FullName -Raw
    foreach ($m in [regex]::Matches($text, '\]\(([^)\s]+)\)')) {
        $target = $m.Groups[1].Value
        if ($target -match '^(https?:|mailto:|#)') { continue }
        $target = ($target -split '#')[0]
        if (-not $target) { continue }
        if (-not (Test-Path (Join-Path $doc.DirectoryName $target))) {
            Write-Host "[doc-check] BROKEN $rel -> $target" -ForegroundColor Yellow
            $fail++
        }
    }
}

if ($fail -gt 0) {
    Write-Host "[doc-check] FAILED ($fail)" -ForegroundColor Red
    exit 1
}

Write-Host '[doc-check] OK' -ForegroundColor Green
