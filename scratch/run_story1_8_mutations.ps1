
$mutations = @(
    @{
        File = "backend/src/main/java/com/pos/users/controller/UserController.java"
        Original = "@PreAuthorize(`"hasAuthority('USER_WRITE')`")`n    public ResponseEntity<ApiResponse<UserResponse>> createUser"
        Mutated = "@PreAuthorize(`"permitAll()`")`n    public ResponseEntity<ApiResponse<UserResponse>> createUser"
        Name = "UserController.createUser PreAuthorize bypass"
        TargetTest = "UserApiIntegrationTests"
    },
    @{
        File = "backend/src/main/java/com/pos/users/service/UserService.java"
        Original = "if (userRepository.findByUsername(request.username()).isPresent()) {"
        Mutated = "if (false) {"
        Name = "UserService duplicate username bypass"
        TargetTest = "UserApiIntegrationTests"
    },
    @{
        File = "backend/src/main/java/com/pos/users/service/UserService.java"
        Original = "if (!callerAuthorities.contains(permissionCode)) {"
        Mutated = "if (false) {"
        Name = "UserService privilege escalation bypass"
        TargetTest = "UserApiIntegrationTests"
    },
    @{
        File = "backend/src/main/java/com/pos/users/controller/UserController.java"
        Original = "@PreAuthorize(`"hasAuthority('USER_READ')`")`n    public ResponseEntity<ApiResponse<UserResponse>> getUser"
        Mutated = "@PreAuthorize(`"permitAll()`")`n    public ResponseEntity<ApiResponse<UserResponse>> getUser"
        Name = "UserController.getUser PreAuthorize bypass"
        TargetTest = "UserApiIntegrationTests"
    },
    @{
        File = "backend/src/main/java/com/pos/users/service/RoleService.java"
        Original = "if (!callerAuthorities.contains(p.getCode())) {"
        Mutated = "if (false) {"
        Name = "RoleService privilege escalation bypass"
        TargetTest = "RoleApiIntegrationTests"
    }
)

$reportPath = "scratch/mutation_report_1.8.txt"
Set-Content $reportPath -Value "Story 1.8 Security Mutation Report`n====================================`n"

foreach ($m in $mutations) {
    Write-Host "Testing mutation: $($m.Name)..."
    $content = Get-Content $m.File -Raw
    $content = $content -replace [regex]::Escape($m.Original), $m.Mutated
    Set-Content $m.File -Value $content -NoNewline
    
    $mvnResult = mvn -B test -Dtest=$($m.TargetTest) 2>&1 | Out-String
    
    if ($mvnResult -match "BUILD SUCCESS") {
        Write-Host "VULNERABILITY: Mutation $($m.Name) SURVIVED (Tests passed when they should fail)" -ForegroundColor Red
        Add-Content $reportPath "FAIL: $($m.Name) SURVIVED"
    } else {
        Write-Host "CAUGHT: Mutation $($m.Name) correctly caused test failure." -ForegroundColor Green
        Add-Content $reportPath "PASS: $($m.Name) CAUGHT"
    }
    
    # Restore
    git checkout -- $($m.File)
}

Write-Host "Mutation testing complete. Check $reportPath"

