# DeployPack.ps1 - Deploy built mod jar to client & server mods, plus the v1.4.1 resource pack to server
$ErrorActionPreference = 'Stop'

$Jar       = 'D:\Users\TermiNexus\Desktop\NeedsOfNature-1.20.1-port-main\build\libs\needsofnature-1.4.4.jar'
$ClientMods = 'D:\Users\TermiNexus\Desktop\HMCL\OLD\.minecraft\versions\1.21.11-Fabric-Fuck\mods'
$ServerMods = 'D:\MCServer\MCN\mods'

# --- Resource pack (original feature) ---
$PackSrc = 'D:\Users\TermiNexus\Desktop\HMCL\OLD\.minecraft\versions\1.21.11-Fabric-Fuck\resourcepacks\needs_of_nature_default_packv1.4.1'
$PackDst = 'D:\MCServer\MCN\world\datapacks\needs_of_nature_default_packv1.4.1'

if (-not (Test-Path -LiteralPath $Jar)) { throw "Built jar not found: $Jar" }

# 1. Client mods: remove previous deploy, back up original, copy new jar
$Old = "$ClientMods\needsofnature-1.0.0.jar"
if (Test-Path -LiteralPath $Old) {
    Remove-Item -LiteralPath $Old -Force
    Write-Output "Removed old client deploy: $Old"
}
$OrigClient = "$ClientMods\needsofnature-1.4.4..jar"
if (Test-Path -LiteralPath $OrigClient) {
    Move-Item -LiteralPath $OrigClient -Destination "$OrigClient.bak" -Force
    Write-Output "Backed up client mod: $OrigClient -> .bak"
}
Copy-Item -LiteralPath $Jar -Destination "$ClientMods\needsofnature-1.4.4.jar" -Force
Write-Output "Client mod deployed: $ClientMods\needsofnature-1.4.4.jar"

# 2. Server mods: back up original, copy new jar (server may be running -> file locked)
$OrigServer = "$ServerMods\needsofnature-1.4.4..jar"
try {
    if (Test-Path -LiteralPath $OrigServer) {
        Move-Item -LiteralPath $OrigServer -Destination "$OrigServer.bak" -Force
        Write-Output "Backed up server mod: $OrigServer -> .bak"
    }
    Copy-Item -LiteralPath $Jar -Destination "$ServerMods\needsofnature-1.4.4.jar" -Force
    Write-Output "Server mod deployed: $ServerMods\needsofnature-1.4.4.jar"
} catch {
    Write-Output "WARNING: Server mod deploy failed - file is locked. Stop the server first, then re-run this script."
    throw $_
}

# 3. Resource pack to server datapacks (original feature)
if (-not (Test-Path -LiteralPath $PackSrc)) { throw "Resource pack not found: $PackSrc" }
if (Test-Path -LiteralPath $PackDst) { Remove-Item -LiteralPath $PackDst -Recurse -Force }
Copy-Item -LiteralPath $PackSrc -Destination $PackDst -Recurse -Force
Write-Output "Resource pack deployed: $PackSrc -> $PackDst"
