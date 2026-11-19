Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and !$line.StartsWith("#") -and $line.Contains("=")) {
        $parts = $line.Split("=", 2)
        $key = $parts[0].Trim()
        $val = $parts[1].Trim()
        [Environment]::SetEnvironmentVariable($key, $val, "Process")
    }
}

# Override with local Docker containers (PostgreSQL 5433, Redis 6380)
[Environment]::SetEnvironmentVariable("DB_HOST", "localhost", "Process")
[Environment]::SetEnvironmentVariable("DB_PORT", "5433", "Process")
[Environment]::SetEnvironmentVariable("DB_NAME", "printhub_test", "Process")
[Environment]::SetEnvironmentVariable("DB_USERNAME", "postgres", "Process")
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "postgres", "Process")
[Environment]::SetEnvironmentVariable("DB_SSL_MODE", "disable", "Process")

[Environment]::SetEnvironmentVariable("REDIS_HOST", "localhost", "Process")
[Environment]::SetEnvironmentVariable("REDIS_PORT", "6380", "Process")
[Environment]::SetEnvironmentVariable("REDIS_SSL", "false", "Process")
[Environment]::SetEnvironmentVariable("SPRING_PROFILES_ACTIVE", "postgres", "Process")

Write-Host "Starting Spring Boot with local Docker Postgres (5433) and Docker Redis (6380)..."
mvn spring-boot:run
