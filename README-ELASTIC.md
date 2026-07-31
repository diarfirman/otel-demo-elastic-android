# OpenTelemetry Demo — Elastic APM + Android

> An Elastic fork of the [OpenTelemetry Demo](https://github.com/open-telemetry/opentelemetry-demo) extended with a native Android app and full Elastic APM integration.

---

## English

### Overview

This project demonstrates end-to-end distributed tracing from a native Android app through a microservices backend, all observable in Elastic APM. The Android app sends OpenTelemetry spans that flow through Envoy (frontend-proxy) and 11 backend services, appearing as a single connected trace in Elastic APM Service Map and APM Traces.

### Architecture

```
Android App (Kotlin + Jetpack Compose)
    │  OTel spans via Elastic APM Android Agent v0.17.0
    ▼
Envoy (frontend-proxy)  ──►  frontend (Next.js)
    │                              │
    └──────────────────────────────┤
                                   ▼
                    checkout ─► cart ─► product-catalog
                        │
                        ├──► currency
                        ├──► shipping ─► quote
                        ├──► payment
                        └──► email
```

All telemetry is collected by the OpenTelemetry Collector (via `opentelemetry-kube-stack` Helm chart) and shipped to **Elastic APM on Elastic Cloud**.

### Key Features

- **End-to-end distributed trace**: one Android user action generates 46+ spans across 11 services
- **Service Map**: full topology from `otel-demo-android` → `frontend-proxy` → `frontend` → backend services
- **User Journey Dashboard**: Kibana dashboard tracking product views → add-to-cart → checkout → order funnels
- **Android OTel instrumentation**: manual spans with `span.makeCurrent()` inside Kotlin coroutines for correct context propagation

### Repository Structure

```
android-app/                    Android demo application (Kotlin + Jetpack Compose)
kubernetes/elastic-helm/
  demo.yml                      Helm values override for opentelemetry-demo chart
  kube-stack-overrides.yml      OTel Collector config (transform processor, peer.service fix)
  CHANGES.md                    All modifications from upstream with root cause explanations
```

### Prerequisites

- GKE cluster (or any Kubernetes cluster)
- Elastic Cloud deployment (Elasticsearch + Kibana + APM)
- Helm 3
- Android Studio (for building the Android app)

### Deployment

#### 1. Deploy the demo stack

```bash
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo update

helm upgrade --install my-otel-demo open-telemetry/opentelemetry-demo \
  --namespace default \
  --values kubernetes/elastic-helm/demo.yml
```

#### 2. Configure the OTel Collector

```bash
helm upgrade opentelemetry-kube-stack open-telemetry/opentelemetry-kube-stack \
  --namespace opentelemetry-operator-system \
  --version 0.12.4 \
  --values kubernetes/elastic-helm/kube-stack-overrides.yml \
  --reuse-values
```

#### 3. Build the Android app

Create `android-app/local.properties` (not committed):

```properties
sdk.dir=/path/to/Android/Sdk
elastic.apm.serverUrl=https://<your-apm-url>:443
elastic.apm.secretToken=<your-secret-token>
frontendProxyUrl=http://<your-cluster-ip>:8080
```

Open `android-app/` in Android Studio and run on a device or emulator.

### Notable Fixes from Upstream

See [`kubernetes/elastic-helm/CHANGES.md`](kubernetes/elastic-helm/CHANGES.md) for the full list. Key ones:

| Issue | Root Cause | Fix |
|---|---|---|
| Android NPE on cart open | `addToCart()` returned raw POST response without product details | Added `getCart()` after POST |
| Checkout 504 timeout | `payment` pod OOMKilled (120Mi limit) | Raised to 300Mi |
| Checkout 500 on all routes | `PRODUCT_REVIEWS_ADDR` missing → gRPC client crash at module level | Added env var to frontend |
| Disconnected distributed trace | `span.startSpan()` called outside coroutine; OTel uses thread-local context | Moved span creation inside `launch {}` + `span.makeCurrent()` |
| Service map missing `frontend-proxy → frontend` edge | Envoy egress spans lack `peer.service` attribute | OTTL transform: `set(peer.service, upstream_cluster)` |

---

## Bahasa Indonesia

### Gambaran Umum

Project ini mendemonstrasikan distributed tracing end-to-end dari aplikasi Android native hingga ke backend microservices, semuanya dapat diobservasi di Elastic APM. Aplikasi Android mengirimkan span OpenTelemetry yang mengalir melalui Envoy (frontend-proxy) dan 11 backend services, dan tampil sebagai satu trace yang terhubung di Elastic APM Service Map dan APM Traces.

### Arsitektur

```
Aplikasi Android (Kotlin + Jetpack Compose)
    │  OTel span via Elastic APM Android Agent v0.17.0
    ▼
Envoy (frontend-proxy)  ──►  frontend (Next.js)
    │                              │
    └──────────────────────────────┤
                                   ▼
                    checkout ─► cart ─► product-catalog
                        │
                        ├──► currency
                        ├──► shipping ─► quote
                        ├──► payment
                        └──► email
```

Seluruh telemetry dikumpulkan oleh OpenTelemetry Collector (via Helm chart `opentelemetry-kube-stack`) dan dikirim ke **Elastic APM di Elastic Cloud**.

### Fitur Utama

- **Distributed trace end-to-end**: satu aksi pengguna Android menghasilkan 46+ span di 11 services
- **Service Map**: topologi lengkap dari `otel-demo-android` → `frontend-proxy` → `frontend` → backend services
- **User Journey Dashboard**: dashboard Kibana yang melacak funnel product view → add-to-cart → checkout → order
- **Android OTel instrumentation**: span manual dengan `span.makeCurrent()` di dalam Kotlin coroutines untuk propagasi context yang benar

### Struktur Repository

```
android-app/                    Aplikasi Android demo (Kotlin + Jetpack Compose)
kubernetes/elastic-helm/
  demo.yml                      Override Helm values untuk chart opentelemetry-demo
  kube-stack-overrides.yml      Konfigurasi OTel Collector (transform processor, fix peer.service)
  CHANGES.md                    Semua perubahan dari upstream beserta penjelasan root cause-nya
```

### Prasyarat

- GKE cluster (atau Kubernetes cluster apapun)
- Elastic Cloud deployment (Elasticsearch + Kibana + APM)
- Helm 3
- Android Studio (untuk build aplikasi Android)

### Deployment

#### 1. Deploy demo stack

```bash
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo update

helm upgrade --install my-otel-demo open-telemetry/opentelemetry-demo \
  --namespace default \
  --values kubernetes/elastic-helm/demo.yml
```

#### 2. Konfigurasi OTel Collector

```bash
helm upgrade opentelemetry-kube-stack open-telemetry/opentelemetry-kube-stack \
  --namespace opentelemetry-operator-system \
  --version 0.12.4 \
  --values kubernetes/elastic-helm/kube-stack-overrides.yml \
  --reuse-values
```

#### 3. Build aplikasi Android

Buat file `android-app/local.properties` (tidak di-commit ke VCS):

```properties
sdk.dir=/path/to/Android/Sdk
elastic.apm.serverUrl=https://<apm-url-anda>:443
elastic.apm.secretToken=<secret-token-anda>
frontendProxyUrl=http://<ip-cluster-anda>:8080
```

Buka folder `android-app/` di Android Studio dan jalankan di perangkat atau emulator.

### Perbaikan Penting dari Upstream

Lihat [`kubernetes/elastic-helm/CHANGES.md`](kubernetes/elastic-helm/CHANGES.md) untuk daftar lengkap. Perbaikan utama:

| Masalah | Root Cause | Perbaikan |
|---|---|---|
| NPE di Android saat buka cart | `addToCart()` mengembalikan response POST mentah tanpa product details | Tambah `getCart()` setelah POST |
| Checkout timeout 504 | Pod `payment` di-kill OOM (limit 120Mi) | Dinaikkan ke 300Mi |
| Checkout 500 di semua routes | `PRODUCT_REVIEWS_ADDR` tidak ada → gRPC client crash di level module | Tambah env var ke frontend |
| Distributed trace tidak terhubung | `span.startSpan()` dipanggil di luar coroutine; OTel pakai thread-local context | Pindahkan span creation ke dalam `launch {}` + `span.makeCurrent()` |
| Edge `frontend-proxy → frontend` tidak muncul di service map | Envoy egress span tidak punya atribut `peer.service` | OTTL transform: `set(peer.service, upstream_cluster)` |
