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
    -Name "M1: Remove CREDIT_READ on GET credit" `
    -File "src\main\java\com\pos\customers\controller\CustomerCreditController.java" `
    -Search "@PreAuthorize(`"hasAuthority('CREDIT_READ')`") // credit get" `
    -Replace "@PreAuthorize(`"permitAll()`") // credit get" `
    -Test "CustomerCreditApiIntegrationTests#inventoryManagerWithoutCreditPermissionIsForbidden"

Run-Mutation `
    -Name "M2: Skip negative-balance guard" `
    -File "src\main\java\com\pos\customers\service\CustomerCreditService.java" `
    -Search "if (newBalance.compareTo(BigDecimal.ZERO) < 0) {" `
    -Replace "if (false && newBalance.compareTo(BigDecimal.ZERO) < 0) {" `
    -Test "CustomerCreditApiIntegrationTests#redeemBeyondBalanceIsBusinessRuleViolation"

Run-Mutation `
    -Name "M3: Skip credit audit write" `
    -File "src\main\java\com\pos\customers\service\CustomerCreditService.java" `
    -Search "auditRecorder.record(AuditEvent.of(" `
    -Replace "if (false) auditRecorder.record(AuditEvent.of(" `
    -Test "CustomerCreditApiIntegrationTests#issueWritesAudit"
