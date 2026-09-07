Output of `org.jwcarman.codec.benchmarks.EncodedSizes`, same 2026-09-06 run as `2026-09-06-apple-m4-max-jdk25.json` and `2026-09-06-compression-ratios.json`.

| Backend | small (bytes) | medium (bytes) |
|---|---:|---:|
| jackson3 | 72 | 9,096 |
| jackson2 | 72 | 9,096 |
| gson | 72 | 9,096 |
| jsonb | 72 | 9,096 |
| fory | 74 | 4,510 |
| protobuf | 35 | 4,815 |
