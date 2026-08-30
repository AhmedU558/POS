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
    $originalContent = Get-Content -Path $File -Raw
    if (-not $originalContent.Contains($Search)) {
        Write-Host "ERROR: Search string not found in $File"
        exit 1
    }

    $mutatedContent = $originalContent.Replace($Search, $Replace)
    [System.IO.File]::WriteAllText((Resolve-Path $File).Path, $mutatedContent, (New-Object System.Text.UTF8Encoding $False))

    $mvnProcess = Start-Process mvn -ArgumentList "-B","-Dtest=$Test","-DfailIfNoTests=false","test" -NoNewWindow -Wait -PassThru
    $exitCode = $mvnProcess.ExitCode

    [System.IO.File]::WriteAllText((Resolve-Path $File).Path, $originalContent, (New-Object System.Text.UTF8Encoding $False))
    $hashAfter = (Get-FileHash -Path $File).Hash
    if ($hashBefore -ne $hashAfter) {
        Write-Host "ERROR: File restoration failed!" -ForegroundColor Red
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
    -Name "M1: Remove PURCHASE_READ on GET list" `
    -File "src\main\java\com\pos\purchases\controller\PurchaseOrderController.java" `
    -Search "@PreAuthorize(`"hasAuthority('PURCHASE_READ')`") // purchase orders list" `
    -Replace "@PreAuthorize(`"permitAll()`") // purchase orders list" `
    -Test "PurchaseOrderApiIntegrationTests#rolesWithoutPurchasePermissionAreForbidden"

Run-Mutation `
    -Name "M2: Skip draft-only check" `
    -File "src\main\java\com\pos\purchases\service\PurchaseOrderService.java" `
    -Search "if (order.getStatus() != PurchaseOrderStatus.DRAFT) {" `
    -Replace "if (false && order.getStatus() != PurchaseOrderStatus.DRAFT) {" `
    -Test "PurchaseOrderApiIntegrationTests#createListGetUpdateSubmitAndRejectNonDraftMutations"

Run-Mutation `
    -Name "M3: Skip create audit" `
    -File "src\main\java\com\pos\purchases\service\PurchaseOrderService.java" `
    -Search "audit(`"PURCHASE_ORDER_CREATED`", saved.getId());" `
    -Replace "if (false) audit(`"PURCHASE_ORDER_CREATED`", saved.getId());" `
    -Test "PurchaseOrderApiIntegrationTests#cancelDraftAndWriteAudit"
