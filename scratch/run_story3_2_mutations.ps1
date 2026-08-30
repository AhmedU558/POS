$ErrorActionPreference = 'Stop'

function Run-Mutation {
    param(
        [string]$Name,
        [string]$File,
        [string]$Search,
        [string]$Replace,
        [string]$Test
    )

    Write-Host ""
    Write-Host "=========================================="
    Write-Host "MUTATION: $Name"
    Write-Host "=========================================="

    $hashBefore = (Get-FileHash -Path $File).Hash
    Write-Host "Original SHA-256: $hashBefore"

    $originalContent = Get-Content -Path $File -Raw
    if (-not $originalContent.Contains($Search)) {
        Write-Host "ERROR: Search string not found in $File"
        Write-Host "Search string was: $Search"
        exit 1
    }

    $mutatedContent = $originalContent.Replace($Search, $Replace)
    [System.IO.File]::WriteAllText((Resolve-Path $File).Path, $mutatedContent, (New-Object System.Text.UTF8Encoding $False))

    Write-Host "Mutation applied. Running $Test ..."

    $mvnProcess = Start-Process mvn -ArgumentList "-B","-Dtest=$Test","-DfailIfNoTests=false","test" -NoNewWindow -Wait -PassThru
    $exitCode = $mvnProcess.ExitCode

    [System.IO.File]::WriteAllText((Resolve-Path $File).Path, $originalContent, (New-Object System.Text.UTF8Encoding $False))
    $hashAfter = (Get-FileHash -Path $File).Hash
    Write-Host "Restored SHA-256: $hashAfter"

    if ($hashBefore -ne $hashAfter) {
        Write-Host "ERROR: File restoration failed! Hashes do not match." -ForegroundColor Red
        exit 1
    }

    if ($exitCode -eq 0) {
        Write-Host "VULNERABILITY: Tests PASSED when they should have FAILED!" -ForegroundColor Red
        exit 1
    } else {
        Write-Host "SUCCESS: Mutation caught. Tests FAILED as expected." -ForegroundColor Green
    }
}

Set-Location (Join-Path $PSScriptRoot "..\backend")

Run-Mutation `
    -Name "M1: Remove INVENTORY_RECEIVE check" `
    -File "src\main\java\com\pos\inventory\controller\InventoryController.java" `
    -Search "@PreAuthorize(`"hasAuthority('INVENTORY_RECEIVE')`")" `
    -Replace "@PreAuthorize(`"permitAll()`")" `
    -Test "InventoryReceiptApiIntegrationTests#cashierWithoutReceivePermissionIsForbidden"

Run-Mutation `
    -Name "M2: Bypass store scope" `
    -File "src\main\java\com\pos\inventory\service\InventoryService.java" `
    -Search "if (!storeScopeEvaluator.canAccess(store.getId())) {" `
    -Replace "if (false && !storeScopeEvaluator.canAccess(store.getId())) {" `
    -Test "InventoryReceiptApiIntegrationTests#wrongStoreScopeIsForbidden"

Run-Mutation `
    -Name "M3: Skip ledger write" `
    -File "src\main\java\com\pos\inventory\service\InventoryService.java" `
    -Search "transactionRepository.save(receipt);" `
    -Replace "if (false) { transactionRepository.save(receipt); }" `
    -Test "InventoryReceiptApiIntegrationTests#receivesStockAndWritesLedgerAndAudit"

Run-Mutation `
    -Name "M4: Remove active-resource check" `
    -File "src\main\java\com\pos\inventory\service\InventoryService.java" `
    -Search "if (!product.isActive()) {" `
    -Replace "if (false && !product.isActive()) {" `
    -Test "InventoryReceiptApiIntegrationTests#inactiveProductIsConflict"

Run-Mutation `
    -Name "M5: Remove pessimistic lock" `
    -File "src\main\java\com\pos\inventory\repository\InventoryBalanceRepository.java" `
    -Search "@Lock(LockModeType.PESSIMISTIC_WRITE)" `
    -Replace "// @Lock(LockModeType.PESSIMISTIC_WRITE)" `
    -Test "InventoryReceiptConcurrencyTests"

Write-Host ""
Write-Host "All mutations caught. Source restored." -ForegroundColor Cyan
