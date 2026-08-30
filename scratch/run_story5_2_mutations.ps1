$ErrorActionPreference = 'Stop'

function Run-Mutation {
    param([string]$Name, [string]$File, [string]$Search, [string]$Replace, [string]$Test)
    $hashBefore = (Get-FileHash -Path $File).Hash
    $originalContent = Get-Content -Path $File -Raw
    if (-not $originalContent.Contains($Search)) { Write-Host "ERROR: Search not found"; exit 1 }
    [System.IO.File]::WriteAllText((Resolve-Path $File).Path, $originalContent.Replace($Search, $Replace), (New-Object System.Text.UTF8Encoding $False))
    $mvnProcess = Start-Process mvn -ArgumentList "-B","-Dtest=$Test","-DfailIfNoTests=false","test" -NoNewWindow -Wait -PassThru
    [System.IO.File]::WriteAllText((Resolve-Path $File).Path, $originalContent, (New-Object System.Text.UTF8Encoding $False))
    if ((Get-FileHash -Path $File).Hash -ne $hashBefore) { Write-Host "ERROR: restore failed"; exit 1 }
    if ($mvnProcess.ExitCode -eq 0) { Write-Host "VULNERABILITY"; exit 1 } else { Write-Host "SUCCESS: $Name caught" }
}

Set-Location (Join-Path $PSScriptRoot "..\backend")

Run-Mutation -Name "M1 permitAll POST" `
    -File "src\main\java\com\pos\purchases\controller\GoodsReceiptController.java" `
    -Search "@PreAuthorize(`"hasAuthority('INVENTORY_RECEIVE')`") // goods receipts create" `
    -Replace "@PreAuthorize(`"permitAll()`") // goods receipts create" `
    -Test "GoodsReceiptApiIntegrationTests#cashierIsForbidden"

Run-Mutation -Name "M2 skip submitted check" `
    -File "src\main\java\com\pos\purchases\service\GoodsReceiptService.java" `
    -Search "if (order.getStatus() != PurchaseOrderStatus.SUBMITTED) {" `
    -Replace "if (false && order.getStatus() != PurchaseOrderStatus.SUBMITTED) {" `
    -Test "GoodsReceiptApiIntegrationTests#draftPoCannotBeReceived"

Run-Mutation -Name "M3 skip audit" `
    -File "src\main\java\com\pos\purchases\service\GoodsReceiptService.java" `
    -Search "auditRecorder.record(AuditEvent.of(" `
    -Replace "if (false) auditRecorder.record(AuditEvent.of(" `
    -Test "GoodsReceiptApiIntegrationTests#createWritesAudit"
