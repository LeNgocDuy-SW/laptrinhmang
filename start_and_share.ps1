[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "SilentlyContinue"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Test-Port8080 {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect("127.0.0.1", 8080, $null, $null)
        $wait = $iar.AsyncWaitHandle.WaitOne(300, $false)
        if ($wait -and $client.Connected) {
            $client.EndConnect($iar)
            $client.Close()
            return $true
        }
        $client.Close()
    } catch {}
    return $false
}

Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host "   KHOI DONG CHAT SERVER & TAO LINK CHIA SE (1-CLICK LOCAL)            " -ForegroundColor Cyan
Write-Host "=======================================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Tu dong bat Server neu chua chay
if (-not (Test-Port8080)) {
    Write-Host "[1/3] Dang khoi dong Chat Server (Cong 5000 & Web 8080)..." -ForegroundColor Yellow
    Start-Process "java" -ArgumentList "-Dfile.encoding=UTF-8", "-cp", ".;lib/*", "server.ServerMain" -WorkingDirectory $root | Out-Null

    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Milliseconds 500
        if (Test-Port8080) {
            $ready = $true
            break
        }
    }

    if (-not $ready) {
        Write-Host "[LOI] Server khong phan hoi tren cong 8080. Hay kiem tra JDK!" -ForegroundColor Red
        Read-Host "Nhan Enter de thoat"
        exit 1
    }
    Write-Host "      -> Server da san sang!" -ForegroundColor Green
} else {
    Write-Host "[1/3] Server dang hoat dong san tren cong 8080!" -ForegroundColor Green
}

# Lay IP mang LAN
$lanIp = "127.0.0.1"
try {
    $lanIp = ([System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
        Where-Object { $_.AddressFamily -eq 'InterNetwork' -and $_.ToString() -notlike '127.*' } |
        Select-Object -First 1).ToString()
} catch {}

# 2. Khoi dong Cloudflare Tunnel (dung HTTP/2 de ket noi nhanh 2-3s, khong bi timeout QUIC)
$cfExe = Join-Path $root "cloudflared.exe"
if (-not (Test-Path $cfExe)) {
    $cmd = Get-Command "cloudflared.exe" -ErrorAction SilentlyContinue
    if ($cmd) {
        $cfExe = $cmd.Source
    } else {
        Write-Host "[THONG BAO] Chua co cloudflared.exe. Dang tu dong tai ve (chi can tai 1 lan duy nhat)..." -ForegroundColor Yellow
        $downloadUrl = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
        try {
            [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12 -bor [System.Net.SecurityProtocolType]::Tls13
            $progressPreference = 'SilentlyContinue'
            Invoke-WebRequest -Uri $downloadUrl -OutFile $cfExe -UseBasicParsing
            Write-Host "            -> Da tai xong cloudflared.exe!" -ForegroundColor Green
        } catch {
            Write-Host "[LOI] Khong the tu dong tai cloudflared.exe: $_" -ForegroundColor Red
            Write-Host "Ban co the tai truc tiep tu link: $downloadUrl va dat vao thu muc du an." -ForegroundColor Yellow
            Read-Host "Nhan Enter de thoat"
            exit 1
        }
    }
}

$logErr = Join-Path $root "cf_tunnel.log"
$logOut = Join-Path $root "cf_out.log"
Remove-Item $logErr, $logOut -Force -ErrorAction SilentlyContinue

Write-Host "[2/3] Dang tao duong link Internet bao mat (doi 3-8 giay)..." -ForegroundColor Yellow
$cfProc = Start-Process $cfExe -ArgumentList "tunnel", "--protocol", "http2", "--url", "http://127.0.0.1:8080" `
    -RedirectStandardError $logErr -RedirectStandardOutput $logOut -WindowStyle Hidden -PassThru

$publicUrl = $null
$registered = $false

try {
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 500
        if ($cfProc.HasExited) { break }

        if (Test-Path $logErr) {
            $text = Get-Content $logErr -Raw -ErrorAction SilentlyContinue
            if (-not $publicUrl -and $text -match "(https://[a-zA-Z0-9-]+\.trycloudflare\.com)") {
                $publicUrl = $Matches[1]
            }
            if ($text -match "Registered tunnel connection") {
                $registered = $true
            }
            if ($publicUrl -and $registered) {
                break
            }
        }
    }

    if ($publicUrl -and $registered) {
        Set-Clipboard -Value $publicUrl
        Write-Host ""
        Write-Host "=======================================================================" -ForegroundColor Green
        Write-Host "  [3/3] SAN SANG! DA TU DONG COPY LINK VAO CLIPBOARD (Ctrl + V)" -ForegroundColor Green
        Write-Host "=======================================================================" -ForegroundColor Green
        Write-Host ""
        Write-Host "  Link gui ban be (4G / khac mang Wi-Fi):" -ForegroundColor White
        Write-Host "     $publicUrl" -ForegroundColor Cyan
        Write-Host ""
        if ($lanIp -and $lanIp -ne "127.0.0.1") {
            Write-Host "  Link cung mang Wi-Fi / LAN:" -ForegroundColor White
            Write-Host "     http://${lanIp}:8080" -ForegroundColor Gray
            Write-Host ""
        }
        Write-Host "  -> Chi can mo Zalo / Messenger va bam Ctrl + V de gui link!" -ForegroundColor Yellow
        Write-Host "  -> Giu nguyen cua so nay trong luc moi nguoi chat (nhan Ctrl + C de tat)." -ForegroundColor Yellow
        Write-Host "=======================================================================" -ForegroundColor Green

        # Tu dong mo trinh duyet vao link de kiem tra ngay
        Start-Process $publicUrl | Out-Null

        Wait-Process -Id $cfProc.Id
    } else {
        Write-Host ""
        Write-Host "[LOI] Khong ket noi duoc Cloudflare Tunnel. Chi tiet log:" -ForegroundColor Red
        if (Test-Path $logErr) { Get-Content $logErr | Select-Object -Last 15 }
        Read-Host "Nhan Enter de thoat"
    }
} finally {
    if ($cfProc -and -not $cfProc.HasExited) {
        Stop-Process -Id $cfProc.Id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $logErr, $logOut -Force -ErrorAction SilentlyContinue
}

