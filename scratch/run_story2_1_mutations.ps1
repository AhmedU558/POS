$ErrorActionPreference = 'Stop'

function Run-Mutation {
    param(
        [string]$Name,
        [string]$File,
        [string]$Search,
        [string]$Replace
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

    Write-Host "Mutation applied. Running tests..."
    
    $mvnProcess = Start-Process mvn -ArgumentList "-B test -Dtest=*CatalogApiIntegration* -DfailIfNoTests=false" -NoNewWindow -Wait -PassThru
    $exitCode = $mvnProcess.ExitCode

    if ($exitCode -eq 0) {
        Write-Host "VULNERABILITY: Tests PASSED when they should have FAILED!" -ForegroundColor Red
        [System.IO.File]::WriteAllText((Resolve-Path $File).Path, $originalContent, (New-Object System.Text.UTF8Encoding $False))
        exit 1
    } else {
        Write-Host "SUCCESS: Mutation caught. Tests FAILED as expected." -ForegroundColor Green
    }

    [System.IO.File]::WriteAllText((Resolve-Path $File).Path, $originalContent, (New-Object System.Text.UTF8Encoding $False))
    $hashAfter = (Get-FileHash -Path $File).Hash
    Write-Host "Restored SHA-256: $hashAfter"

    if ($hashBefore -ne $hashAfter) {
        Write-Host "ERROR: File restoration failed! Hashes do not match." -ForegroundColor Red
        exit 1
    }
}

cd backend

Run-Mutation -Name "M1: Remove PRODUCT_WRITE check on Brand Create" -File "C:\Users\ahmed\OneDrive\Desktop\POS\backend\src\main\java\com\pos\catalog\controller\BrandController.java" -Search "@PreAuthorize(`"hasAuthority('PRODUCT_WRITE')`")" -Replace "@PreAuthorize(`"permitAll()`")"

Run-Mutation -Name "M2: Bypass depth check in CategoryService" -File "C:\Users\ahmed\OneDrive\Desktop\POS\backend\src\main\java\com\pos\catalog\service\CategoryService.java" -Search "if (depth > MAX_DEPTH) {" -Replace "if (false) {"

Write-Host "All mutations caught! Foundation is secure." -ForegroundColor Cyan
