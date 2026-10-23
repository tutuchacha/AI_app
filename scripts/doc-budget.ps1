#!/usr/bin/env pwsh
# 文档预算检查：行数 + 字符 + 估算 token。超限返回非 0，提交前跑一次。

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$limits = @(
  [pscustomobject]@{ File = 'AGENTS.md'; Lines = 75; Chars = 3400 }
  [pscustomobject]@{ File = 'README.md'; Lines = 80; Chars = 3400 }
  [pscustomobject]@{ File = 'docs/STATUS.md'; Lines = 35; Chars = 1800 }
  [pscustomobject]@{ File = 'docs/INDEX.md'; Lines = 45; Chars = 2200 }
  [pscustomobject]@{ File = 'docs/00-架构方案.md'; Lines = 450; Chars = 17000 }
  [pscustomobject]@{ File = 'docs/01-环境准备.md'; Lines = 220; Chars = 6500 }
  [pscustomobject]@{ File = 'docs/04-对标-yu-ai-agent.md'; Lines = 200; Chars = 5500 }
  [pscustomobject]@{ File = 'docs/ARCHITECTURE.md'; Lines = 150; Chars = 5000 }
  [pscustomobject]@{ File = 'docs/DOC-GOVERNANCE.md'; Lines = 100; Chars = 4000 }
  [pscustomobject]@{ File = 'docs/02-接口约定.md'; Lines = 120; Chars = 4500 }
)

foreach ($file in Get-ChildItem 'docs/modules/*.md') {
  $relative = $file.FullName.Substring($root.Length + 1).Replace('\', '/')
  $limits += [pscustomobject]@{ File = $relative; Lines = 120; Chars = 5000 }
}

function Get-TokenEstimate([string]$text) {
  $cjk = ([regex]::Matches($text, '[\u4e00-\u9fff]')).Count
  $other = $text.Length - $cjk
  return [math]::Round($cjk + ($other / 4))
}

$rows = foreach ($item in $limits) {
  $path = Join-Path $root $item.File
  if (-not (Test-Path $path)) { continue }
  $text = Get-Content -Raw -Encoding UTF8 $path
  $lineCount = (Get-Content -Encoding UTF8 $path | Measure-Object -Line).Lines
  $charCount = $text.Length
  $over = ($lineCount -gt $item.Lines) -or ($charCount -gt $item.Chars)
  [pscustomobject]@{
    File      = $item.File
    Lines     = $lineCount
    LineLimit = $item.Lines
    Chars     = $charCount
    CharLimit = $item.Chars
    Tokens    = (Get-TokenEstimate $text)
    Status    = if ($over) { 'OVER' } else { 'OK' }
  }
}

$rows | Format-Table -AutoSize
$total = ($rows | Measure-Object -Property Tokens -Sum).Sum
Write-Host ''
Write-Host ("全部文档约 {0} tokens；最小开工包 ≈ AGENTS + STATUS + 模块 §0 ≈ 1.3k tokens" -f $total)

$bad = @($rows | Where-Object { $_.Status -eq 'OVER' })
if ($bad.Count -gt 0) {
  Write-Host ''
  Write-Host '超限文件：' -ForegroundColor Red
  foreach ($row in $bad) {
    Write-Host ("  {0}（{1}/{2} 行，{3}/{4} 字符）" -f $row.File, $row.Lines, $row.LineLimit, $row.Chars, $row.CharLimit)
  }
  exit 1
}

Write-Host '全部文档在预算内。' -ForegroundColor Green
exit 0
