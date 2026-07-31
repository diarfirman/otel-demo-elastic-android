# Changes from Upstream Repository

Perubahan yang dilakukan pada konfigurasi Kubernetes/Helm untuk menjalankan OpenTelemetry Demo dengan Elastic APM stack.

---

## Android App Fixes (2026-07-31)

Perbaikan pada Android demo app (`android-app/`) untuk crash, checkout failure, dan distributed trace connectivity.

### 1. NPE saat buka cart setelah add item

**File:** `app/src/main/java/co/elastic/demo/android/data/repository/ShopRepository.kt`

**Root cause:** `addToCart()` mengembalikan raw response dari POST `/cart` yang tidak mengandung product details. `CartScreen` langsung akses `item.product` → NPE.

**Fix:** Tambah `api.getCart(sessionId)` setelah POST, konsisten dengan `removeCartItem` dan `updateCartItemQuantity`.

```diff
  suspend fun addToCart(...): Result<CartResponse> = runCatching {
      api.addToCart(AddToCartRequest(...))
+     api.getCart(sessionId)
  }
```

---

### 2. Checkout 504 — payment OOMKilled

**Root cause:** Pod `payment` di-kill oleh OOM killer (memory limit 120Mi terlalu rendah). Restart 14+ kali, setiap checkout request timeout setelah 15 detik.

**Fix:** Naikkan memory limit di `kubernetes/elastic-helm/demo.yml`:

```diff
+ payment:
+   resources:
+     limits:
+       memory: 300Mi
+     requests:
+       memory: 150Mi
```

---

### 3. Checkout 500 — PRODUCT_REVIEWS_ADDR missing

**Root cause:** Frontend deployment tidak punya env var `PRODUCT_REVIEWS_ADDR`. File `src/frontend/gateways/rpc/ProductReview.gateway.ts` membuat gRPC client di module level dengan target string kosong → `Could not parse target name ""` → Next.js Turbopack mencrash seluruh server-side bundle termasuk checkout route.

**Fix:** Set env var di `kubernetes/elastic-helm/demo.yml`:

```diff
+ frontend:
+   envOverrides:
+     - name: PRODUCT_REVIEWS_ADDR
+       value: "product-reviews:8080"
```

Catatan: service `product-reviews` tidak exist di cluster — DNS resolution gagal saat runtime, tapi tidak crash karena checkout tidak memanggil product reviews. Yang penting module-level gRPC client tidak error saat inisialisasi.

---

### 4. Checkout form pre-filled

**File:** `app/src/main/java/co/elastic/demo/android/ui/screens/CheckoutScreen.kt`

Form checkout diisi dengan demo data default agar tidak perlu diisi manual saat demo:
- Email: `demo@example.com`
- Address: `1600 Amphitheatre Pkwy, Mountain View, CA 94043, US`
- Currency: `USD`

---

### 5. Distributed trace tidak terhubung (berbeda traceId)

**File:** `app/src/main/java/co/elastic/demo/android/viewmodel/ShopViewModel.kt`

**Root cause:** Span dibuat dengan `tracer.spanBuilder(...).startSpan()` di luar `viewModelScope.launch { }`. OpenTelemetry menggunakan thread-local context. Saat OkHttp interceptor berjalan di dalam coroutine (thread berbeda), tidak ada active span → interceptor membuat root span baru dengan traceId berbeda.

**Fix:** Pindahkan span creation ke dalam coroutine dan panggil `span.makeCurrent()` sebelum HTTP calls:

```kotlin
fun addToCart(productId: String, quantity: Int = 1) {
    viewModelScope.launch {
        val span = tracer.spanBuilder("user.add_to_cart").startSpan()
        val scope = span.makeCurrent()  // set thread-local context INSIDE coroutine
        try {
            repository.addToCart(...)
        } finally {
            scope.close()  // harus ditutup di finally
            span.end()
        }
    }
}
```

Sama untuk `placeOrder()`. Setelah fix, satu checkout menghasilkan 46 spans terhubung di 11 services: `otel-demo-android → frontend-proxy → frontend → checkout → cart → product-catalog → currency → shipping → quote → payment → email`.

---

## OTel Collector / Service Map Fixes (2026-07-31)

### 6. Service map edge `frontend-proxy → frontend` tidak muncul

**File:** `kubernetes/elastic-helm/kube-stack-overrides.yml`

**Root cause:** Envoy emit egress spans dengan nama `router frontend egress` dan attribute `upstream_cluster = "frontend"`, tapi tidak set `peer.service`. Elastic APM butuh `peer.service` (atau `service.target.name`) untuk menggambar edge di service map.

**Fix:** Tambah OTTL statement di transform processor gateway collector:

```diff
  # Set peer.service from upstream_cluster so Elastic APM service map draws the edge
+ - set(span.attributes["peer.service"], span.attributes["upstream_cluster"]) where
+   resource.attributes["service.name"] == "frontend-proxy" and
+   span.attributes["upstream_cluster"] != nil
```

Juga tambahkan `exporters: { debug: {} }` secara eksplisit agar tidak hilang saat helm upgrade.

**Apply:**
```bash
helm upgrade opentelemetry-kube-stack open-telemetry/opentelemetry-kube-stack \
  --namespace opentelemetry-operator-system \
  --version 0.12.4 \
  --values kubernetes/elastic-helm/kube-stack-overrides.yml \
  --reuse-values
```

**Catatan penting:** Sebelum fix ini, `kube-stack-overrides.yml` berisi transform processor tapi **tidak pernah di-apply** ke cluster (`processors: {}` di CRD). Helm upgrade di atas adalah pertama kalinya transform processor aktif di production.

---

Perubahan yang dilakukan pada `kubernetes/elastic-helm/demo.yml` untuk kompatibilitas image Elastic `2.2.1` dengan upstream Helm chart `open-telemetry/opentelemetry-demo@0.38.3`.

## demo.yml

### 1. Hapus komponen `llm` dan `product-reviews`

```diff
- llm:
-   enabled: false
- product-reviews:
-   enabled: false
```

Upstream chart `0.38.3` tidak mengenal kedua komponen ini (Elastic-specific). Keberadaannya menyebabkan schema validation error saat `helm install`/`helm upgrade`.

---

### 2. `accounting` — DB credentials dan memory limit

```diff
+ accounting:
+   envOverrides:
+     - name: DB_CONNECTION_STRING
+       value: "Host=postgresql;Username=astronomy_user;Password=astronomy_password;Database=astronomy_db"
+   resources:
+     limits:
+       memory: 256Mi
```

- Chart upstream men-set credentials `otelu/otelp/otel` yang tidak ada di database yang diinisialisasi image Elastic.
- Image 2.2.1 menggunakan database `astronomy_db` dengan user `astronomy_user`.
- Memory limit dinaikkan dari 120Mi (default) ke 256Mi karena OOMKilled.

---

### 3. `flagd` — upgrade image ke v0.16.1

```diff
  flagd:
+   imageOverride:
+     repository: ghcr.io/open-feature/flagd
+     tag: "v0.16.1"
```

Payment service 2.2.1 menggunakan `@openfeature/flagd-provider@0.15.0` yang memanggil `flagd.evaluation.v2` gRPC API. Chart upstream pin di `v0.12.8` yang belum support API tersebut, menyebabkan payment 100% error rate.

---

### 4. `postgresql` — override ke standard `postgres:14`

```diff
+ postgresql:
+   imageOverride:
+     repository: postgres
+     tag: "14"
```

Image `ghcr.io/elastic/opentelemetry-demo:2.2.1-postgresql` tidak di-publish di GHCR sehingga menyebabkan `ImagePullBackOff`. Diganti dengan image standard `postgres:14`.

---

### 5. `product-catalog` — tambah `DB_CONNECTION_STRING`

```diff
  product-catalog:
+   envOverrides:
+     - name: DB_CONNECTION_STRING
+       value: "postgres://astronomy_user:astronomy_password@postgresql:5432/astronomy_db?sslmode=disable"
```

Baru di versi 2.2.1 — product-catalog membaca data produk dari PostgreSQL (bukan hanya dari file). Tanpa env var ini, service langsung crash dengan exit code 1 tanpa log apapun.

---

## Inisialisasi Database (Manual, di luar Helm)

Karena `postgres:14` adalah image kosong, schema dan data harus diinisialisasi manual dari `src/postgresql/init.sql` milik Elastic repo.

### Cara apply (pertama kali atau setelah pod recreate)

```bash
# 1. Buat ConfigMap dari init.sql
gh api "repos/elastic/opentelemetry-demo/contents/src/postgresql/init.sql" \
  --jq '.content' | base64 -d \
  | grep -v "pg_stat_statements" \
  > /tmp/pg_init.sql

kubectl create configmap postgresql-init --from-file=init.sql=/tmp/pg_init.sql

# 2. Copy dan jalankan SQL di pod postgresql
PG_POD=$(kubectl get pod -l "app.kubernetes.io/component=postgresql" \
  -o jsonpath="{.items[0].metadata.name}")

cat /tmp/pg_init.sql | kubectl exec -i $PG_POD -- bash -c "cat > /tmp/init.sql"
kubectl exec $PG_POD -- psql -U root -d otel -f /tmp/init.sql
```

### Yang dibuat oleh init.sql

| Schema | Tabel | Keterangan |
|---|---|---|
| `catalog` | `products` | 10 produk untuk product-catalog service |
| `accounting` | `order`, `shipping`, `orderitem` | Data transaksi untuk accounting service |
| `reviews` | `productreviews` | Review produk untuk product-reviews service |

Database: `astronomy_db`, User: `astronomy_user` / `astronomy_password`

### Catatan penting

Chart schema melarang `volumes` dan `volumeMounts` di level component, sehingga ConfigMap tidak bisa di-mount otomatis ke postgresql pod. Jika postgresql pod restart atau recreate (tidak ada PersistentVolume), **data hilang** dan SQL harus di-apply ulang manual.
