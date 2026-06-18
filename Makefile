# SuperBizAgent Makefile
# Windows-friendly version

SERVER_URL = http://localhost:9900
UPLOAD_API = $(SERVER_URL)/api/upload
DOCS_DIR = aiops-docs
HEALTH_CHECK_API = $(SERVER_URL)/milvus/health
DOCKER_COMPOSE_FILE = vector-database.yml
MILVUS_CONTAINER = milvus-standalone

.PHONY: help init start stop restart check upload clean up down status wait list-docs test-upload logs

help:
	@cmd /c echo SuperBizAgent Makefile
	@cmd /c echo(
	@cmd /c echo Available commands:
	@cmd /c echo   make init       - start docker, start app, wait, upload docs
	@cmd /c echo   make up         - start Docker Compose services
	@cmd /c echo   make down       - stop Docker Compose services
	@cmd /c echo   make status     - show Docker container status
	@cmd /c echo   make start      - start Spring Boot app in background
	@cmd /c echo   make stop       - stop Spring Boot app
	@cmd /c echo   make restart    - restart Spring Boot app
	@cmd /c echo   make check      - check app health
	@cmd /c echo   make wait       - wait until app is ready
	@cmd /c echo   make upload     - upload markdown docs
	@cmd /c echo   make list-docs  - list markdown docs
	@cmd /c echo   make logs       - show Spring Boot log
	@cmd /c echo   make clean      - clean temporary files
	@cmd /c echo(
	@cmd /c echo Examples:
	@cmd /c echo   make up
	@cmd /c echo   make start
	@cmd /c echo   make wait
	@cmd /c echo   make upload

init:
	@cmd /c echo Starting SuperBizAgent init...
	@$(MAKE) up
	@$(MAKE) start
	@$(MAKE) wait
	@$(MAKE) upload
	@cmd /c echo Init finished.
	@cmd /c echo API: $(SERVER_URL)
	@cmd /c echo Attu UI: http://localhost:8000

up:
	@cmd /c echo Checking Docker Compose file...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "if (!(Test-Path '$(DOCKER_COMPOSE_FILE)')) { Write-Host 'Missing compose file: $(DOCKER_COMPOSE_FILE)'; exit 1 }"
	@cmd /c echo Starting Docker Compose services...
	@docker-compose -f $(DOCKER_COMPOSE_FILE) up -d
	@cmd /c echo Waiting for Milvus port 19530...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "$$ok=$$false; for ($$i=1; $$i -le 60; $$i++) { $$r = Test-NetConnection -ComputerName localhost -Port 19530 -InformationLevel Quiet; if ($$r) { $$ok=$$true; break }; Write-Host ('waiting for Milvus... ' + $$i + '/60'); Start-Sleep -Seconds 2 }; if ($$ok) { Write-Host 'Milvus port is ready.' } else { Write-Host 'Milvus port is not ready. Check docker logs.'; exit 1 }"
	@docker ps --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

down:
	@cmd /c echo Stopping Docker Compose services...
	@docker-compose -f $(DOCKER_COMPOSE_FILE) down
	@cmd /c echo Docker Compose stopped.

status:
	@cmd /c echo Docker container status:
	@docker ps -a --filter "name=milvus" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

start:
	@cmd /c echo Starting Spring Boot app in background...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path 'server.pid') { Write-Host 'server.pid already exists. Run make stop or make clean first if needed.' }; $$p = Start-Process -FilePath 'cmd.exe' -ArgumentList '/c mvn spring-boot:run > server.log 2>&1' -PassThru; $$p.Id | Out-File -Encoding ascii 'server.pid'; Write-Host ('Spring Boot start command executed. PID: ' + $$p.Id); Write-Host 'Log file: server.log'"

wait:
	@cmd /c echo Waiting for Spring Boot app...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "$$ok=$$false; for ($$i=1; $$i -le 90; $$i++) { try { Invoke-WebRequest -Uri '$(HEALTH_CHECK_API)' -UseBasicParsing -TimeoutSec 3 | Out-Null; $$ok=$$true; break } catch { Write-Host ('waiting for app... ' + $$i + '/90'); Start-Sleep -Seconds 2 } }; if ($$ok) { Write-Host 'Spring Boot app is ready.' } else { Write-Host 'Spring Boot app is not ready. Check server.log'; exit 1 }"

check:
	@cmd /c echo Checking Spring Boot app health...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -Uri '$(HEALTH_CHECK_API)' -UseBasicParsing -TimeoutSec 3 | Out-Null; Write-Host 'Server is running: $(SERVER_URL)' } catch { Write-Host 'Server is not reachable.'; exit 1 }"

upload:
	@cmd /c echo Uploading documents from $(DOCS_DIR)...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "if (!(Test-Path '$(DOCS_DIR)')) { Write-Host 'Docs directory does not exist: $(DOCS_DIR)'; exit 1 }"
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "$$files = Get-ChildItem '$(DOCS_DIR)' -Filter *.md; if ($$files.Count -eq 0) { Write-Host 'No markdown files found.'; exit 1 }"
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "$$files = Get-ChildItem '$(DOCS_DIR)' -Filter *.md; foreach ($$file in $$files) { Write-Host ('Uploading: ' + $$file.Name); curl.exe -X POST '$(UPLOAD_API)' -F ('file=@' + $$file.FullName) -H 'Accept: application/json' }"
	@cmd /c echo Upload finished.
stop:
	@cmd /c echo Stopping Spring Boot app...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "$$processes = Get-CimInstance Win32_Process | Where-Object { $$_.CommandLine -like '*spring-boot:run*' -or $$_.CommandLine -like '*super-biz-agent*' }; if ($$processes) { $$processes | ForEach-Object { try { Stop-Process -Id $$_.ProcessId -Force -ErrorAction SilentlyContinue } catch {} }; Write-Host 'Spring Boot processes stopped.' } else { Write-Host 'No Spring Boot process found.' }; Remove-Item 'server.pid' -Force -ErrorAction SilentlyContinue"

restart:
	@$(MAKE) stop
	@$(MAKE) start

clean:
	@cmd /c echo Cleaning temporary files...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item 'server.pid','server.log' -Force -ErrorAction SilentlyContinue; if (Test-Path 'uploads') { Get-ChildItem 'uploads' -Filter *.tmp -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue }; Write-Host 'Clean finished.'"

list-docs:
	@cmd /c echo Markdown docs in $(DOCS_DIR):
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path '$(DOCS_DIR)') { Get-ChildItem '$(DOCS_DIR)' -Filter *.md | Format-Table Name, Length, LastWriteTime } else { Write-Host 'Docs directory does not exist: $(DOCS_DIR)' }"

test-upload:
	@cmd /c echo Testing single file upload...
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "$$file='$(DOCS_DIR)/cpu_high_usage.md'; if (Test-Path $$file) { curl.exe -X POST '$(UPLOAD_API)' -F ('file=@' + $$file) -H 'Accept: application/json' } else { Write-Host 'Test file does not exist.'; exit 1 }"

logs:
	@powershell -NoProfile -ExecutionPolicy Bypass -Command "if (Test-Path 'server.log') { Get-Content 'server.log' -Tail 100 -Wait } else { Write-Host 'server.log does not exist.' }"