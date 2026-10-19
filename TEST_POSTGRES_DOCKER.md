# Hướng dẫn Môi trường Test PostgreSQL Docker Local

Tài liệu hướng dẫn quản lý môi trường PostgreSQL local độc lập phục vụ chạy test integration, kiểm tra concurrency và diễn tập (rehearsal) migration trước khi áp dụng lên Supabase.

---

## 1. Thông số môi trường

- **Compose File:** `docker-compose.test.yml`
- **Compose Project Name:** `printhub-test`
- **Container Name:** `printhub-test-postgres`
- **Image:** `postgres:16-alpine`
- **Host Port:** `127.0.0.1:5433` (Ánh xạ vào container port 5432)
- **Database Name:** `printhub_test`
- **Username:** `postgres`
- **Password:** `postgres`
- **Volume:** `printhub_test_pgdata`
- **Spring Boot Profile:** `test-local` (Cấu hình tại `src/main/resources/application-test-local.properties`)

---

## 2. Các lệnh quản lý Docker

### 2.1. Khởi động Container
```powershell
docker compose -f docker-compose.test.yml up -d
```

### 2.2. Kiểm tra trạng thái Healthcheck
```powershell
docker ps --filter "name=printhub-test-postgres"
```
Khi container sẵn sàng, cột `STATUS` sẽ hiển thị `Up ... (healthy)`.

### 2.3. Xem Log Container
```powershell
docker logs printhub-test-postgres
```

### 2.4. Dừng Container (Giữ nguyên dữ liệu)
```powershell
docker compose -f docker-compose.test.yml stop
```

### 2.5. Xóa Container và Reset sạch Dữ liệu Test
```powershell
docker compose -f docker-compose.test.yml down -v
```
*(Lệnh này chỉ xóa volume `printhub_test_pgdata`, không ảnh hưởng tới container Redis `exe-dev` hay dữ liệu dự án khác).*

---

## 3. Thực thi SQL và Chạy Ứng dụng với Profile Test

### 3.1. Chạy SQL Script vào Database Test
```powershell
docker exec -i printhub-test-postgres psql -U postgres -d printhub_test -f /path/to/script.sql
```
Hoặc dùng Powershell:
```powershell
Get-Content scripts/db-test/01_expand.sql | docker exec -i printhub-test-postgres psql -U postgres -d printhub_test
```

### 3.2. Chạy Spring Boot với Profile `test-local`
```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=test-local
```
Hoặc chạy kiểm thử:
```powershell
mvn test -Dspring.profiles.active=test-local
```
Profile này sử dụng chế độ `spring.jpa.hibernate.ddl-auto=validate` để phát hiện ngay lập tức bất kỳ sự sai lệch nào giữa JPA Entity và Schema Database thực tế.
