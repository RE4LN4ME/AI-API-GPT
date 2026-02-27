param(
    [string]$BaseUrl = "http://localhost:8081",
    [int]$Count = 3,
    [string]$ApiKey = $env:OPENAI_API_KEY
)

if ([string]::IsNullOrWhiteSpace($ApiKey)) {
    Write-Output "STATUS=FAIL REASON=OPENAI_API_KEY missing"
    exit 1
}

$headers = @{
    "X-API-Key" = $ApiKey
    "Content-Type" = "application/json"
}

try {
    $conv = Invoke-WebRequest -Uri "$BaseUrl/api/conversations" -Headers @{ "X-API-Key" = $ApiKey } -UseBasicParsing -TimeoutSec 10
    Write-Output "CONVERSATIONS_HTTP=$($conv.StatusCode)"
} catch {
    if ($_.Exception.Response -ne $null) {
        $resp = $_.Exception.Response
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $body = $reader.ReadToEnd()
        Write-Output "STATUS=FAIL STEP=conversations HTTP=$($resp.StatusCode.value__) BODY=$body"
        exit 2
    }
    Write-Output "STATUS=FAIL STEP=conversations ERROR=$($_.Exception.Message)"
    exit 2
}

$ok = 0
for ($i = 1; $i -le $Count; $i++) {
    $payload = @{ message = "smoke test #$i $(Get-Date -Format o)" } | ConvertTo-Json
    try {
        $chat = Invoke-WebRequest -Uri "$BaseUrl/api/chat/completions" -Method Post -Headers $headers -Body $payload -UseBasicParsing -TimeoutSec 90
        Write-Output "CHAT_$i HTTP=$($chat.StatusCode)"
        if ($chat.StatusCode -eq 200) { $ok++ }
    } catch {
        if ($_.Exception.Response -ne $null) {
            $resp = $_.Exception.Response
            $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $body = $reader.ReadToEnd()
            Write-Output "CHAT_$i HTTP=$($resp.StatusCode.value__) BODY=$body"
        } else {
            Write-Output "CHAT_$i ERROR=$($_.Exception.Message)"
        }
    }
}

if ($ok -eq $Count) {
    Write-Output "STATUS=OK SUCCESS_COUNT=$ok TOTAL=$Count"
    exit 0
}

Write-Output "STATUS=FAIL SUCCESS_COUNT=$ok TOTAL=$Count"
exit 3
