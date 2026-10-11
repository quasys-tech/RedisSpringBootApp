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
| `REDIS_KEY_PREFIX` | Redis Enterprise ACL Multi-Tenancy / Namespace ön eki | `quasys:` |
| `REDIS_PROTOCOL` | Redis protokol sürümü (`RESP2` veya `RESP3`, ACL için RESP2 önerilir) | `RESP2` |
| `REDIS_POOL_ENABLED` | Bağlantı havuzu (Pooling) aktif/pasif | `true` |
| `REDIS_SSL_ENABLED` | TLS/SSL bağlantısı (OpenShift route için varsayılan true) | `true` |
| `REDIS_SSL_USE_SNI` | TLS el sıkışmasında SNI (Server Name Indication) gönderimi | `true` |
| `REDIS_SSL_DISABLE_PEER_VERIFY` | Self-signed veya OCP dahili CA sertifikalarını kabul etme | `true` |
| `REDIS_CLUSTER_NODES` | OSS Cluster API düğüm listesi (OpenShift içi için örn: host:port, dışarıdan erişim için boş bırakılır) | Boş (Proxy modu) |
| `REDIS_RECONNECT_DELAY_MAX` | Redis koptuğunda denemeler arasındaki maksimum bekleme tavanı | `30s` |

---

## 🛡️ Redis Enterprise ACL & Multi-Tenancy (Key Prefix) Desteği

Redis Enterprise üzerinde tek bir veritabanını birden fazla takım veya mikroservis paylaştığında, **Redis 6+ ACL (Access Control Lists)** kuralları ile her kullanıcıya yalnızca kendi ön ekiyle başlayan anahtarlara erişim izni tanımlanır:

```text
user team_alpha on >password ~team_alpha:* +@all
user team_beta  on >password ~team_beta:*  +@all
```

Bu projede geliştirilen şeffaf **Key Prefixing** mekanizması sayesinde:
1. **Spring Cache Abstraction (`@Cacheable`, `@CachePut`, `@CacheEvict`)**:
   * Ön ek `quasys:` olarak ayarlandığında `items` cache'i için üretilen anahtar formatı:  
     `quasys:items:1`, `quasys:items:2` olur.
   * Çift iki nokta (`::`) ortadan kaldırılarak RedisInsight ve GUI araçlarında boş klasör oluşması engellenmiştir (`quasys` $\rightarrow$ `items` $\rightarrow$ `1`).
2. **`RedisTemplate` Doğrudan İşlemleri**:
   * Özel `PrefixStringRedisSerializer` ile `redisTemplate.opsForValue().set("mykey", ...)` çağrıldığında arka planda `quasys:mykey` yazılır.
   * Okuma yapılırken ön ek otomatik olarak şeffaf biçimde soyulur.
3. **Konfigürasyon**:
   * `REDIS_KEY_PREFIX=""` verilirse ön ek devre dışı kalır ve standart anahtar yapısına dönülür.
   * `REDIS_KEY_PREFIX="myteam"` (iki nokta konulmasa dahi) otomatik olarak `myteam:` şeklinde normalize edilir.

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
| `GET` | `/api/info` | Aktif Lettuce, Pooling (`poolingEnabled`), Key Prefix, TLS/SNI ve DB parametrelerini döner |
| `GET` | `/api/items/{id}` | `@Cacheable` testi (İlk çağrı ~1 sn simüle DB, sonrakiler sub-ms Redis cache) |
| `PUT` | `/api/items/{id}` | `@CachePut` testi (DB ve cache güncelleme) |
| `DELETE` | `/api/items/{id}` | `@CacheEvict` testi (DB ve cache kaydını silme) |
| `POST` | `/api/items/pipeline-demo?count=50` | `executePipelined` toplu yazma testi |
| `POST` | `/api/pool/test-concurrency?threads=20&totalRequests=100` | Havuz üzerinden çoklu iş parçacıklı (multi-threaded) eşzamanlı yük testi |
| `GET` | `/api/cluster/topology` | Canlı Redis OSS Cluster topolojisini (Shard'lar, IP/Port'lar, Master/Replica ve Hash Slot aralıkları) inceler |

---

## 📖 Uygulamanın Çalışma Mantığı ve Endpoint Rehberi (Developer Guide)

Bu bölüm, projeyi inceleyen veya üzerinde geliştirme yapacak mühendislerin uygulamanın iç mimarisini ve uç noktalarını (endpoints) kolayca anlaması için hazırlanmıştır.

```
                  ┌────────────────────────────────────────────────────────┐
                  │                 Spring Boot Application                │
                  │                                                        │
                  │   [ RedisDemoController ]                              │
                  │        │                │                              │
                  │        ▼                ▼                              │
                  │  [ ItemService ]   [ RedisTemplate ]                   │
                  │   (@Cacheable)    (PrefixSerializer)                   │
                  │        │                │                              │
                  │        ▼                ▼                              │
                  │   [ LettuceConnectionFactory ]                         │
                  │        │ (Connection Pooling: max 16)                  │
                  │        ▼                                               │
                  │   [ Lettuce Driver (Netty I/O) ]                       │
                  └────────┬───────────────────────────────────────────────┘
                           │ TLS / SSL + SNI (Port 40743)
                           ▼
                  ┌────────────────────────────────────────────────────────┐
                  │      OpenShift Router / Ingress (Public Host)          │
                  │      ossdb-redis-ent-tst.apps.ocp-qua-test...          │
                  └────────┬───────────────────────────────────────────────┘
                           │ Tünel / Dahili Köprü
                           ▼
                  ┌────────────────────────────────────────────────────────┐
                  │          Redis Enterprise DMC Proxy Pod                │
                  │        (Kullanıcı: quasysuser, ACL: ~quasys:*)         │
                  └────────┬───────────────────────────────────────────────┘
                           │ Dahili Pod Yönlendirmesi
                           ▼
                  ┌────────────────────────────────────────────────────────┐
                  │               Redis Shard Pod (Port 12021)             │
                  │            [ quasys:items:1 ] [ quasys:items:2 ]       │
                  └────────────────────────────────────────────────────────┘
```

---

### 1. Uygulama Nasıl Başlar ve Nasıl Çalışır?

1. **Uygulama Başlangıcı (`RedisEnterpriseApplication`):**
   * Spring Boot ayağa kalkarken `application.yml` dosyasındaki `spring.data.redis.*` bloğunu okur.
   * [`RedisConfigurationProperties.java`](file:///Users/abdulmelikkalkan/Developer/Quasys/Redis/RedisApps/SpringBoot/src/main/java/com/quasys/redis/config/RedisConfigurationProperties.java) sınıfı tip-güvenli (type-safe) olarak bu değerleri bağlar.

2. **Lettuce İstemcisi ve Soket Yapılandırması (`RedisConfiguration`):**
   * Netty tabanlı düşük seviye TCP ayarları (`keepAlive: true`, `tcpNoDelay: true`, `connectTimeout: 5s`) devreye alınır.
   * OpenShift Router'ı için zorunlu olan **TLS ve SNI (`SNIHostName`)** konfigüre edilir.
   * ACL uyumluluğu için **RESP2 / RESP3** protokol anlaşması yapılır.
   * Ağ kopmalarına karşı **Exponential Backoff (`reconnectDelay: 100ms - 30s`)** stratejisi yüklenir.

3. **Bağlantı Havuzu (Connection Pooling):**
   * `commons-pool2` devrede olduğu için Spring Boot arka planda önceden açılmış hazır soketlerden oluşan 16'lık bir havuz (`GenericObjectPool`) tahsis eder.

4. **Multi-Tenancy & Key Prefixleme (`PrefixStringRedisSerializer`):**
   * Redis Enterprise ACL kısıtlamasına (`~quasys:*`) tam uyum için tüm önbellek ve `RedisTemplate` komutlarına otomatik olarak `quasys:` ön eki takılır.

5. **Graceful Degradation (`CacheErrorHandler`):**
   * Eğer Redis geçici olarak kesilirse veya failover olursa, Spring Cache hataları loglar (`WARN`) ve uygulamanın çökmesini engelleyerek sorguyu sessizce veritabanına devreder (fallback).

---

### 2. Endpoint'lerin Detaylı İncelemesi ve Kullanımı

---

#### 🟢 1. Canlılık ve Sağlık Kontrolü: `GET /api/health`

* **Amacı:** Redis Enterprise bağlantısının sağlıklı ve ayakta olduğunu doğrular.
* **Nasıl Çalışır?** Redis'e hafif bir `PING` komutu gönderir, ağ gidiş-dönüş süresini (`latencyMs`) hesaplar. Eğer Redis koptuysa veya ACL/şifre hatası varsa HTTP 503 döner ve **gerçek kök nedeni (`rootCause`)** ekranda gösterir.

**Örnek İstek:**
```bash
curl -s http://localhost:8080/api/health
```

**Başarılı Yanıt (HTTP 200):**
```json
{
  "status": "UP",
  "redisPing": "PONG",
  "latencyMs": 18,
  "timestamp": "2026-10-11T17:30:00",
  "message": "Connected successfully to Redis Enterprise!"
}
```

---

#### ℹ️ 2. Çalışma Anı ve Konfigürasyon Teşhisi: `GET /api/info`

* **Amacı:** Uygulamanın o an hangi Redis hostuna, hangi sürücüyle, hangi havuz limitleriyle ve hangi prefix ile bağlı olduğunu tek bir ekranda raporlar.

**Örnek İstek:**
```bash
curl -s http://localhost:8080/api/info
```

**Başarılı Yanıt (HTTP 200):**
```json
{
  "connection": {
    "host": "ossdb-redis-ent-tst.apps.ocp-qua-test.quasys.com.tr",
    "port": 40743,
    "username": "quasysuser",
    "databaseIndex": 0,
    "keyPrefix": "quasys:",
    "timeout": "PT5S"
  },
  "client": {
    "driver": "Lettuce (Netty-based reactive driver)",
    "poolingEnabled": true,
    "shareNativeConnection": true,
    "poolConfiguration": {
      "maxActive": 16,
      "maxIdle": 8,
      "minIdle": 2,
      "maxWait": "PT3S"
    },
    "protocol": "RESP3",
    "autoReconnect": true
  },
  "socketTuning": {
    "keepAlive": true,
    "tcpNoDelay": true,
    "connectTimeout": "PT5S"
  },
  "sslTls": {
    "enabled": true,
    "useSni": true,
    "disablePeerVerification": true
  },
  "cacheTtlsSeconds": {
    "items": 300,
    "users": 600,
    "reports": 3600
  }
}
```

---

#### ⚡ 3. Cache-Aside Okuma Testi: `GET /api/items/{id}`

* **Amacı:** Spring `@Cacheable` anotasyonunun çalışma mekanizmasını ve Redis önbellek performansını gösterir.
* **Nasıl Çalışır?**
  * **1. Çağrı (Cache Miss):** Veri Redis'te yoktur. `ItemService`, yapay olarak 1000 ms bekletilen yavaş veritabanı simülasyonuna gider. DB'den veriyi alır ve Redis'e `quasys:items:1` adıyla JSON olarak kaydeder.
    * *Süre:* **~1020 ms** (Konsol logu: `[DB ACCESS] Fetching item 1 from database...`)
  * **2. Çağrı (Cache Hit):** Veri artık Redis'tedir. DB metodu **hiç çalıştırılmaz**, veri doğrudan Redis RAM'inden döner.
    * *Süre:* **< 5 ms!** (Konsol logunda DB erişimi görülmez).

**Örnek İstek:**
```bash
# İlk çağrı (Cache Miss -> Yavaş)
curl -s http://localhost:8080/api/items/1

# İkinci çağrı (Cache Hit -> Anında)
curl -s http://localhost:8080/api/items/1
```

**Dönen Yanıt (HTTP 200):**
```json
{
  "id": "1",
  "name": "Enterprise SSD",
  "category": "Hardware",
  "price": 299.99,
  "createdAt": "2026-10-11T17:00:00"
}
```

> **Redis'te Ne Oluşur?**  
> RedisInsight'ta `quasys` $\rightarrow$ `items` $\rightarrow$ `1` şeklinde temiz bir hiyerarşide saklanır. Boş klasör (`::`) oluşmaz.

---

#### ✏️ 4. Cache Güncelleme Testi: `PUT /api/items/{id}`

* **Amacı:** Spring `@CachePut` anotasyonunu test eder.
* **Nasıl Çalışır?** Nesneyi veritabanında güncellerken aynı anda Redis önbelleğindeki kaydı da güncel veriyle ezer. Bir sonraki `GET` çağrısı anında güncel veriyi önbellekten okur.

**Örnek İstek:**
```bash
curl -X PUT http://localhost:8080/api/items/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Enterprise NVMe SSD Gen5", "category": "Hardware", "price": 499.99}'
```

---

#### 🗑️ 5. Cache Temizleme Testi: `DELETE /api/items/{id}`

* **Amacı:** Spring `@CacheEvict` anotasyonunu test eder.
* **Nasıl Çalışır?** Kaydı veritabanından siler ve Redis'teki `quasys:items:1` anahtarını bellekten yok eder (invalidation). Sonrasında yapılacak bir `GET` çağrısı tekrar veritabanına gitmek zorunda kalır.

**Örnek İstek:**
```bash
curl -X DELETE http://localhost:8080/api/items/1
```

---

#### 🚀 6. Toplu Yazma ve Pipelining Testi: `POST /api/items/pipeline-demo?count={N}`

* **Amacı:** Ağdaki gidiş-dönüş sürelerini (Round Trip Time - RTT) sıfırlayan **Redis Pipelining** tekniğini gösterir.
* **Nasıl Çalışır?** Verilen adet kadar (`count`) nesneyi tek tek ağ üzerinden göndermek yerine, `redisTemplate.executePipelined()` ile tek bir TCP soket tamponunda paketleyip Redis'e fırlatır.

**Örnek İstek:**
```bash
curl -X POST "http://localhost:8080/api/items/pipeline-demo?count=500"
```

**Dönen Yanıt (HTTP 200):**
```json
{
  "itemsProcessed": 500,
  "elapsedMs": 34,
  "pipelineResultsCount": 500,
  "message": "Successfully executed pipelined batch write to Redis."
}
```
*(500 adet nesnenin TLS üzerinden Redis Enterprise'a yazılması sadece ~34 ms sürer).*

---

#### 🧪 7. Connection Pool Eşzamanlı Yük Testi: `POST /api/pool/test-concurrency`

* **Amacı:** Apache Commons Pool 2 bağlantı havuzunun eşzamanlı çoklu thread altında sorunsuz çalıştığını kanıtlar.
* **Nasıl Çalışır?** Java `ExecutorService` ile örneğin **20 paralel iş parçacığı (threads)** açar ve havuzdan ödünç alınan bağlantılarla aynı anda **100 adet işlem** (`SET` + `GET`) koşturur. İşlem bitince bağlantıların havuza sağlıklı iade edildiğini ve hata sayısının 0 olduğunu raporlar.

**Örnek İstek:**
```bash
curl -X POST "http://localhost:8080/api/pool/test-concurrency?threads=20&totalRequests=100"
```

**Dönen Yanıt (HTTP 200):**
```json
{
  "threads": 20,
  "totalRequests": 100,
  "successCount": 100,
  "errorCount": 0,
  "totalElapsedMs": 240,
  "avgLatencyPerRequestMs": 2.4,
  "message": "Concurrent pool load test completed successfully."
}
```

---

#### 🌐 8. Canlı Cluster Topolojisi İnceleme: `GET /api/cluster/topology`

* **Amacı:** Redis Enterprise kümesindeki shard'ları, rollerini (Master/Replica), OpenShift pod adreslerini ve sorumlu oldukları Hash Slot aralıklarını okunaklı JSON olarak inceler.
* **Nasıl Çalışır?** Redis'e `CLUSTER NODES` komutunu gönderir ve yanıtı parse eder.

**Örnek İstek:**
```bash
curl -s http://localhost:8080/api/cluster/topology
```

**Dönen Yanıt (HTTP 200):**
```json
{
  "clusterModeConfigured": false,
  "nodesCount": 1,
  "nodes": [
    {
      "nodeId": "2",
      "address": "10.131.4.91:12021",
      "flags": "myself,master",
      "masterId": null,
      "linkState": "connected",
      "servedSlots": [
        "0-16383"
      ]
    }
  ],
  "rawOutput": "2 10.131.4.91:12021 myself,master - 0 0 0 connected 0-16383\n"
}
```
*(Bu çıktı; kümenin 1 adet master shard'dan oluştuğunu, OpenShift içindeki pod adresinin `10.131.4.91:12021` olduğunu ve tüm `0-16383` slotlarının bu shard üzerinde olduğunu açıkça gösterir).*
