# OTel Demo Shop — Android App

An Android frontend for the [OpenTelemetry Demo](https://opentelemetry.io/docs/demo/) telescope shop, built with Kotlin + Jetpack Compose and instrumented with the [Elastic APM Android Agent](https://www.elastic.co/guide/en/apm/agent/android/current/intro.html).

## Features

- Browse the telescope product catalog in a responsive 2-column grid
- View product details with full description and category chips
- Add products to cart, adjust quantities, and remove items
- Full checkout flow with currency selection
- Automatic HTTP tracing via Elastic APM (OkHttp instrumentation)
- Custom OpenTelemetry spans for key user actions:
  - `user.view_product`
  - `user.add_to_cart`
  - `user.begin_checkout`
  - `user.place_order`

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- The OpenTelemetry Demo running and accessible (locally or on Kubernetes)
- (Optional) An Elastic Cloud deployment with APM Server for trace ingestion

## Setup

### 1. Configure local.properties

Copy the example file and fill in your values:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```properties
# Elastic APM Server URL — leave empty to disable APM
elastic.apm.serverUrl=https://YOUR_APM_SERVER.apm.gcp.elastic-cloud.com

# Elastic APM Secret Token
elastic.apm.secretToken=YOUR_SECRET_TOKEN

# OpenTelemetry Demo frontend proxy URL
# Android Emulator pointing to host: http://10.0.2.2:8080
# Physical device or Kubernetes: use the actual external IP
frontendProxyUrl=http://10.0.2.2:8080
```

**Important:** `local.properties` is git-ignored and must never be committed.

### 2. Open in Android Studio

Open the `android-app/` directory in Android Studio as a project root.

### 3. Start the OpenTelemetry Demo

Make sure the demo is running. For local Docker Compose:

```bash
cd ..  # back to the opentelemetry-demo root
docker compose up
```

The frontend proxy will be available on port 8080. From an Android Emulator, `http://10.0.2.2:8080` maps to the host machine's localhost.

For Kubernetes deployments, update `frontendProxyUrl` in `local.properties` with the external LoadBalancer IP.

### 4. Build and Run

- Select an emulator or connected device in Android Studio
- Click **Run** (Shift+F10)

If APM is configured, traces will appear in Kibana under **Observability → APM** within a few seconds of user activity.

## Project Structure

```
app/src/main/java/co/elastic/demo/android/
├── OtelDemoApp.kt              # Application class — initializes Elastic APM
├── MainActivity.kt             # Single activity host
├── data/
│   ├── model/                  # Kotlin data classes (Product, Cart, Checkout)
│   ├── api/
│   │   ├── ShopApi.kt          # Retrofit interface for all endpoints
│   │   └── ApiClient.kt        # OkHttpClient + Retrofit factory
│   └── repository/
│       └── ShopRepository.kt   # Data access layer, wraps API calls in Result<T>
├── viewmodel/
│   └── ShopViewModel.kt        # Single shared ViewModel with StateFlow<ShopUiState>
└── ui/
    ├── theme/                   # Elastic brand colors, Material3 theme, typography
    ├── navigation/
    │   └── NavGraph.kt          # Compose Navigation setup
    └── screens/
        ├── ProductListScreen.kt
        ├── ProductDetailScreen.kt
        ├── CartScreen.kt
        └── CheckoutScreen.kt
```

## API Endpoints Used

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/products` | List all products |
| GET | `/api/products/{id}` | Single product |
| GET | `/api/cart?sessionId=` | Enriched cart (includes product details) |
| POST | `/api/cart` | Add item to cart |
| DELETE | `/api/cart` | Empty cart |
| POST | `/api/checkout?currencyCode=` | Place order |
| GET | `/api/currency` | List supported currencies |
| GET | `/api/recommendations` | Product recommendations |

## Telemetry

The app uses the Elastic APM Android Agent, which:

1. **Auto-instruments** HTTP calls made via OkHttp (propagates W3C `traceparent` headers)
2. **Auto-instruments** Activity and Fragment lifecycle events
3. Provides access to the **OpenTelemetry API** for custom spans

Custom spans are created in `ShopViewModel` using:
```kotlin
val tracer = GlobalOpenTelemetry.getTracer("otel-demo-android")
val span = tracer.spanBuilder("user.add_to_cart")
    .setAttribute("product.id", productId)
    .startSpan()
// ... do work ...
span.end()
```

## Notes

- The session ID (UUID) is generated once per ViewModel instance and reused as the cart's `userId`
- Cart quantity updates use a clear-and-re-add strategy since the demo API only supports full cart reset
- Cleartext HTTP traffic is permitted via `network_security_config.xml` for demo convenience — restrict this in production
