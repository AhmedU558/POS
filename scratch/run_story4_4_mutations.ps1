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
    -Name "M1: Remove SUPPLIER_READ on GET products list" `
    -File "src\main\java\com\pos\suppliers\controller\SupplierController.java" `
    -Search "@PreAuthorize(`"hasAuthority('SUPPLIER_READ')`") // supplier products list" `
    -Replace "@PreAuthorize(`"permitAll()`") // supplier products list" `
    -Test "SupplierProductApiIntegrationTests#cashierWithoutSupplierPermissionIsForbidden"

Run-Mutation `
    -Name "M2: Skip unknown-product check" `
    -File "src\main\java\com\pos\suppliers\service\SupplierProductService.java" `
    -Search "if (!products.containsKey(productId)) {" `
    -Replace "if (false && !products.containsKey(productId)) {" `
    -Test "SupplierProductApiIntegrationTests#unknownProductIsNotFound"

Run-Mutation `
    -Name "M3: Skip association audit write" `
    -File "src\main\java\com\pos\suppliers\service\SupplierProductService.java" `
    -Search "auditRecorder.record(AuditEvent.of(" `
    -Replace "if (false) auditRecorder.record(AuditEvent.of(" `
    -Test "SupplierProductApiIntegrationTests#replaceWritesAudit"
