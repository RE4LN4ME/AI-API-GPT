param(
    [string]$EnvFilePath = ".env",
    [string]$VariableName = "OPENAI_API_KEY"
)

function Get-Sha256Hex([string]$text) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha.ComputeHash($bytes)
    } finally {
        $sha.Dispose()
    }
    return -join ($hash | ForEach-Object { $_.ToString("x2") })
}

if (-not (Test-Path $EnvFilePath)) {
    Write-Output "STATUS=FAIL REASON=.env file not found"
    exit 1
}

$line = Get-Content $EnvFilePath | Where-Object { $_ -like "$VariableName=*" } | Select-Object -First 1
if (-not $line) {
    Write-Output "STATUS=FAIL REASON=$VariableName missing in .env"
    exit 1
}

$fileValue = ($line -split "=", 2)[1].Trim()
$osValue = [Environment]::GetEnvironmentVariable($VariableName, "Process")
if ([string]::IsNullOrWhiteSpace($osValue)) {
    $osValue = [Environment]::GetEnvironmentVariable($VariableName, "User")
}
if ([string]::IsNullOrWhiteSpace($osValue)) {
    $osValue = [Environment]::GetEnvironmentVariable($VariableName, "Machine")
}

if ([string]::IsNullOrWhiteSpace($fileValue) -or [string]::IsNullOrWhiteSpace($osValue)) {
    Write-Output "STATUS=FAIL REASON=empty key value"
    exit 1
}

$fileHash = Get-Sha256Hex $fileValue
$osHash = Get-Sha256Hex $osValue

if ($fileHash -eq $osHash) {
    Write-Output "STATUS=OK FILE_HASH_PREFIX=$($fileHash.Substring(0,8)) OS_HASH_PREFIX=$($osHash.Substring(0,8))"
    exit 0
}

Write-Output "STATUS=FAIL REASON=key mismatch FILE_HASH_PREFIX=$($fileHash.Substring(0,8)) OS_HASH_PREFIX=$($osHash.Substring(0,8))"
exit 2
