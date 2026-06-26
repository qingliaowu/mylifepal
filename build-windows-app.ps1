$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = Join-Path $root "windows\MyLifePal.Windows\MyLifePal.Windows.csproj"

dotnet build $project -c Debug
