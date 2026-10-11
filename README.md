# Redis Enterprise & Spring Boot (Lettuce) Best Practices Reference Project

Bu proje, **Redis Enterprise** veritabanına bağlanacak kurumsal Spring Boot uygulamaları için resmi Redis ve Spring Data yönergelerine dayanan referans mimariyi ve **best practice** (en iyi pratik) konfigürasyonlarını içerir.

Müşterilerinize doğrudan sunabileceğiniz, üretime hazır (production-ready) bir şablondur.

---

## 📌 İçerilen Best Practice'ler ve Mimarisi

### 1. Lettuce Client & Bağlantı Yönetimi
* **Non-Blocking & Multiplexing**: Lettuce, Netty tabanlı olup tek bir TCP bağlantısı üzerinden komutları multiplex ederek asenkron/non-blocking şekilde yönetir (`shareNativeConnection = true`).
* **Configurable Connection Pooling (`commons-pool2`)**:
  * İstenirse `spring.data.redis.lettuce.pool.enabled` parametresi ile bağlantı havuzu (pooling) açılıp kapatılabilir.
  * `enabled: true`: Apache Commons Pool 2 devreye girer. Yüksek eşzamanlılık ve bloklayıcı komutlar için idealdir.
  * `enabled: false`: Pooling devre dışı bırakılır, Lettuce'ın hafif ve yüksek performanslı tek bağlantı multiplexing mekanizması kullanılır.
* **TCP Socket Optimizasyonu**:
  * `keepAlive(true)`: Cloud Load Balancer, AWS NLB, Azure LB, OpenShift Ingress veya kurumsal güvenlik duvarlarının boştaki (idle) bağlantıları sessizce sonlandırmasını engeller.
  * `tcpNoDelay(true)`: Nagle algoritmasını devre dışı bırakarak paket gecikmesini minimize eder.
  * `connectTimeout`: Bağlantı denemelerinde thread'lerin kilitlenmesini önler.
* **Resilience (Dayanıklılık)**:
  * `autoReconnect(true)`: Ağ kesintisi veya failover durumunda otomatik yeniden bağlanma.
  * `pingBeforeActivateConnection(true)`: Bağlantı aktifleştirilmeden önce PING ile canlılığını doğrular.

### 2. TLS/SSL ve SNI (Server Name Indication) Desteği
OpenShift Container Platform (OCP) ve bulut ortamlarındaki Ingress/HAProxy router'lar üzerinden TLS ile Redis Enterprise'a bağlanırken:
* **`use-sni: true`**: İstemci, TLS ClientHello el sıkışması sırasında hedef sunucu adını (`SNIHostName`) iletir. OpenShift HAProxy Router'ın trafiği doğru Redis servisine yönlendirebilmesi için bu **zorunludur**.
* **`sni-server-name`**: Boş bırakıldığında otomatik olarak `host` adı kullanılır; özel Ingress tanımları için manuel de verilebilir.
* **`disable-peer-verification`**: Kurumsal test ortamlarında dahili CA veya self-signed sertifika kullanılıyorsa sertifika zincir hatası almamak için opsiyonel olarak devre dışı bırakılabilir.

### 3. Redis Enterprise Endpoint & Cluster Desteği
Redis Enterprise veritabanları iki farklı mimaride çalışabilir:
1. **Single Endpoint (DMC Proxy - Standart)**: Redis Enterprise'ın çoklu iş parçacıklı koordinatör proxy'si (DMC) arkasındaki shard'ları şeffaf bir şekilde yönetir. İstemci tek bir endpoint (`host:port`) görür.
2. **OSS Cluster API**: Veritabanında OSS Cluster API aktifse istemci doğrudan shard topolojisini görür.
   * Bu modda **Cluster Topology Refresh** devrede olmalıdır:
     * `enableAllAdaptiveRefreshTriggers()`: `MOVED`, `ASK`, `PERSISTENT_RECONNECTS` olaylarında topolojiyi anında günceller.
     * `enablePeriodicRefresh(30s)`: Periyodik arka plan yenilemesi sağlar.
     * `dynamicRefreshSources(true)`: Kümedeki tüm düğümleri topoloji keşfinde kullanır.

### 4. `spring.data.redis.database` Nedir? (Neden Enterprise'da Her Zaman 0 Olmalı?)
* **Klasik Standalone Redis'te (OSS)** 0-15 arası numaralandırılmış 16 mantıksal veritabanı bulunur (`SELECT 1` komutu).
* **Redis Cluster ve Redis Enterprise'da** mimari sharding üzerine kurulu olduğu için **çoklu veritabanı (multi-database) DESTEKLENMEZ**. `SELECT` komutu yasaktır.
* Redis Enterprise'da veri izolasyonu mantıksal DB numarasıyla değil, **gerçek bağımsız veritabanları oluşturularak** (ayrı RAM, ayrı persistence, ayrı port) veya **key prefix'leri** (`cache::`, `users::`) ile sağlanır.
* Bu yüzden `spring.data.redis.database` değeri **her zaman `0`** olarak kalmalıdır.

### 5. Spring Cache Abstraction Best Practices
* **Varsayılan ve Cache Bazlı TTL**: Belleğin sınırsız büyümesini ve OOM hatalarını engellemek için her cache grubu için TTL (`items`: 5dk, `users`: 10dk, `reports`: 1 saat) tanımlanmıştır.
* **Null Value Caching Koruması**: Cache penetration/pollution risklerine karşı null değerlerin varsayılanda önbelleğe alınması engellenmiştir.
* **Güvenli JSON Serileştirme**: Güvensiz ve okunaksız Java default serialization (`JdkSerializationRedisSerializer`) yerine `GenericJackson2JsonRedisSerializer` + `JavaTimeModule` konfigüre edilmiştir.
* **Graceful Degradation (`CacheErrorHandler`)**: Redis geçici olarak erişilemez olduğunda veya failover sırasında HTTP 500 hatası vermek yerine hata loglanır ve sistem doğrudan veri tabanına / kaynak servise düşerek (fallback) çalışmaya kesintisiz devam eder.

### 6. Pipelining Best Practice
* `executePipelined`: Toplu yazma/okuma işlemlerinde her komut için ayrı ağ gidiş-dönüşü (RTT) yapmak yerine komutları paketleyerek tek seferde iletir.

---

## 🚀 Projeyi Çalıştırma

### Gereksinimler
* Java 21+
* Maven 3.9+

### Derleme (Build)
```bash
mvn clean compile
```

### Uygulamayı Başlatma
```bash
mvn spring-boot:run
```
veya paketleyip çalıştırmak için:
```bash
mvn package -DskipTests
java -jar target/redis-enterprise-demo-0.0.1-SNAPSHOT.jar
```

Uygulama varsayılan olarak `8080` portunda ayağa kalkar.

---

## ⚙️ Redis Enterprise Bağlantı Parametreleri

`application.yml` dosyasından veya ortam değişkenleri (Environment Variables) üzerinden konfigüre edilebilir:

| Parametre / Ortam Değişkeni | Açıklama | Varsayılan Değer |
|---|---|---|
| `REDIS_HOST` | Redis Enterprise Endpoint adresi | `cachedb-redis-ent-tst.apps.ocp-qua-test.quasys.com.tr` |
| `REDIS_PORT` | Redis Enterprise Port | `40743` |
| `REDIS_USERNAME` | Redis kullanıcısı (Redis ACL) | `default` |
| `REDIS_PASSWORD` | Veritabanı şifresi | `vOFCeFcI` |
| `REDIS_DATABASE` | Veritabanı indeksi (Enterprise için her zaman 0) | `0` |
| `REDIS_POOL_ENABLED` | Bağlantı havuzu (Pooling) aktif/pasif | `true` |
| `REDIS_SSL_ENABLED` | TLS/SSL bağlantısı (OpenShift route için varsayılan true) | `true` |
| `REDIS_SSL_USE_SNI` | TLS el sıkışmasında SNI (Server Name Indication) gönderimi | `true` |
| `REDIS_SSL_DISABLE_PEER_VERIFY` | Self-signed veya OCP dahili CA sertifikalarını kabul etme | `true` |
| `REDIS_CLUSTER_MODE` | OSS Cluster API aktif ise `true` yapılır | `false` |
| `REDIS_RECONNECT_DELAY_MAX` | Redis koptuğunda denemeler arasındaki maksimum bekleme tavanı | `30s` |

---

## ⏱️ Zaman Aşımı, TTL ve Süre Parametreleri Özeti

Uygulamada yapılandırılmış tüm zaman aşımı ve periyot ayarları:

| Parametre | Süre | Görevi ve Best Practice Amacı |
|---|---|---|
| `cache.redis.time-to-live` | `600000ms` (10 dk) | **Varsayılan Cache Ömrü**: Verilerin bellekte sonsuza dek kalmasını ve bellek şişmesini (OOM) önler. |
| `cache-ttls.items` | `300s` (5 dk) | Ürün/Item verileri için özel TTL süresi. |
| `cache-ttls.users` | `600s` (10 dk) | Kullanıcı verileri için özel TTL süresi. |
| `cache-ttls.reports` | `3600s` (1 saat) | Rapor ve analitik veriler için uzun ömürlü TTL süresi. |
| `data.redis.timeout` | `5000ms` (5 sn) | **Komut Yanıt Timeout'u**: Yanıt gelmediğinde thread kilitlenmesini (Thread Starvation) önler. |
| `data.redis.connect-timeout` | `5000ms` (5 sn) | **TCP/TLS Bağlantı Timeout'u**: İlk el sıkışma için maksimum bekleme süresi. |
| `lettuce.shutdown-timeout` | `2000ms` (2 sn) | **Graceful Shutdown**: Uygulama kapanırken hatta devam eden isteklerin bitmesini bekleme süresi. |
| `pool.max-wait` | `3000ms` (3 sn) | **Havuz Bekleme Süresi**: Tüm bağlantılar doluysa boşta bağlantı bekleme üst sınırı. |
| `pool.time-between-eviction-runs` | `30000ms` (30 sn) | **Havuz Temizleme Periyodu**: Atıl ve bayat bağlantıları kontrol edip temizleme sıklığı. |
| `reconnect-delay-max` | `30s` | **Yeniden Bağlanma Tavanı**: Redis koptuğunda arka plan denemeleri arasındaki maksimum bekleme tavanı. |
| `topology-refresh.periodic-refresh-interval` | `30s` | **Cluster Topoloji Yenileme**: OSS Cluster modunda küme haritasını periyodik güncelleme sıklığı. |

---

## 📡 Test ve Doğrulama Uç Noktaları (REST API)

| Metot | Endpoint | Açıklama |
|---|---|---|
| `GET` | `/api/health` | Redis bağlantısını `PING` atarak test eder |
| `GET` | `/api/info` | Aktif Lettuce, Pooling (`poolingEnabled`), TLS/SNI ve DB parametrelerini döner |
| `GET` | `/api/items/{id}` | `@Cacheable` testi (İlk çağrı ~1 sn simüle DB, sonrakiler sub-ms Redis cache) |
| `PUT` | `/api/items/{id}` | `@CachePut` testi (DB ve cache güncelleme) |
| `DELETE` | `/api/items/{id}` | `@CacheEvict` testi (DB ve cache kaydını silme) |
| `POST` | `/api/items/pipeline-demo?count=50` | `executePipelined` toplu yazma testi |
