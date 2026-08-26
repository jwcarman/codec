# Benchmarks

Numbers from the `codec-benchmarks` module (JMH), so the guides' speed and
size claims are reproducible rather than folklore. **One machine, one run:**
Apple M4 Max, 64 GB, OpenJDK 25.0.3, codec 0.8.0-SNAPSHOT, 2026-08-26.
Throughput mode, 1 fork, 3 × 1 s warm-up, 5 × 1 s measurement. The raw JMH
output is in `codec-benchmarks/results/`; treat the relative ordering as the
finding, not the absolute figures.

Compression payloads: **small** is a 104-byte JSON record, **medium** an 8 KB
JSON document, **large** 1 MB of pseudo-English prose. Backend payloads: a
**small** four-field record and a **medium** order with 100 line items (nested
records, a list, strings and numbers).

Deflate is measured at levels 1, 6 (the default) and 9; gzip shares its engine
and adds only framing, so it appears at the default level alone. zstd is
measured at 1, 3 (the default), 9 and 19.

## What the numbers say

- **For medium and large payloads, zstd and LZ4 are in a different league
  from the JDK.** zstd 3 encodes 1 MB at ~590 MB/s against deflate 6's ~35 MB/s
  and decodes twice as fast; LZ4 encodes at ~750 MB/s and decodes at ~1.8 GB/s.
  Even the JDK's fastest level (deflate 1, ~235 MB/s) is well behind zstd 1
  (~620 MB/s) while producing a worse ratio (26.0% vs 23.2%).
- **For tiny payloads the JDK wins.** At 104 bytes gzip and deflate out-run
  zstd and LZ4 on both encode and decode — the fixed cost of a JNI stream
  dominates, and there is nothing to compress anyway (every transform *grows*
  the small payload).
- **Levels buy ratio slowly and cost speed quickly.** deflate 9 is ~45% slower
  than deflate 6 for 0.4 points of ratio; zstd 9 is ~6× slower than zstd 3 for
  0.5 points; zstd 19 encodes at 6 MB/s for the best ratio of all (16.2%).
  The defaults are defaults for a reason.
- **Ratio is not zstd's advantage at the default level.** On the prose payload
  zstd 3 lands at 21.7% of the input against deflate 6's 19.2%; only zstd 19
  beats it. zstd's win is speed at a comparable ratio.
- **LZ4-HC is a decode-side optimisation.** It compresses ~28× slower than
  plain LZ4 (slower than gzip) for a better ratio, and decodes fastest of
  anything measured. Use it for write-rarely, read-constantly data.
- **The text encodings are negligible next to any backend or transform**, with
  the pure-Java Base32 the slowest of them (~440 MB/s).
- **Binary backends pull further ahead as the payload grows.** On the small
  record Fory decodes ~6× faster than Jackson 2; on the 100-item order it is
  ~10× faster on decode and ~15× on encode (1.5 M orders/s), and Protobuf is
  ~5× faster than the JSON backends. Their output is also half the size:
  4.4 KB (Fory) and 4.8 KB (Protobuf) against 9.1 KB of JSON.
- **Among the JSON backends, Jackson 2 decodes fastest** in this run — twice
  as fast as Jackson 3 on the order — and JSON-B (Yasson) is the slowest at
  both sizes.
- **`BoundedDataKeyStrategy` doubles encrypt throughput on small payloads**
  (1.0 M vs 0.42 M ops/s) by amortising the KEK wrap; decode is unchanged,
  since every message is unwrapped on its own.

## Compression ratios

Compressed size and percentage of the input, from `CompressionRatios`:

| Transform | small | medium | large |
|---|---|---|---|
| gzip | 115 (110.6%) | 1,647 (19.8%) | 201,172 (19.2%) |
| deflate1 | 103 (99.0%) | 1,846 (22.1%) | 273,057 (26.0%) |
| deflate6 | 103 (99.0%) | 1,635 (19.6%) | 201,160 (19.2%) |
| deflate9 | 103 (99.0%) | 1,606 (19.3%) | 197,182 (18.8%) |
| zstd1 | 102 (98.1%) | 1,614 (19.4%) | 242,999 (23.2%) |
| zstd3 | 102 (98.1%) | 1,590 (19.1%) | 227,860 (21.7%) |
| zstd9 | 102 (98.1%) | 1,508 (18.1%) | 222,392 (21.2%) |
| zstd19 | 103 (99.0%) | 1,467 (17.6%) | 169,684 (16.2%) |
| lz4 | 123 (118.3%) | 2,704 (32.4%) | 491,989 (46.9%) |
| lz4hc | 123 (118.3%) | 2,335 (28.0%) | 282,916 (27.0%) |

## Encoded sizes by backend

From `EncodedSizes`:

| Backend | small (bytes) | medium (bytes) |
|---|---:|---:|
| jackson3 | 72 | 9,096 |
| jackson2 | 72 | 9,096 |
| gson | 72 | 9,096 |
| jsonb | 72 | 9,096 |
| fory | 37 | 4,422 |
| protobuf | 35 | 4,815 |

## Throughput

### Compression — small payload (104 bytes)

| Transform | encode ops/s | encode MB/s | decode ops/s | decode MB/s |
|---|---:|---:|---:|---:|
| gzip | 257,121 | 27 | 588,837 | 61 |
| deflate1 | 244,586 | 25 | 611,054 | 64 |
| deflate6 | 237,097 | 25 | 608,134 | 63 |
| deflate9 | 249,599 | 26 | 599,277 | 62 |
| zstd1 | 156,297 | 16 | 214,671 | 22 |
| zstd3 | 98,265 | 10 | 189,968 | 20 |
| zstd9 | 20,191 | 2 | 177,769 | 18 |
| zstd19 | 2,829 | 0 | 180,396 | 19 |
| lz4 | 390,673 | 41 | 502,106 | 52 |
| lz4hc | 269,637 | 28 | 490,529 | 51 |

### Compression — medium payload (8,338 bytes)

| Transform | encode ops/s | encode MB/s | decode ops/s | decode MB/s |
|---|---:|---:|---:|---:|
| gzip | 25,589 | 213 | 119,413 | 996 |
| deflate1 | 56,339 | 470 | 99,850 | 833 |
| deflate6 | 24,435 | 204 | 103,773 | 865 |
| deflate9 | 18,350 | 153 | 101,781 | 849 |
| zstd1 | 64,046 | 534 | 97,610 | 814 |
| zstd3 | 41,703 | 348 | 94,445 | 787 |
| zstd9 | 11,106 | 93 | 98,411 | 821 |
| zstd19 | 838 | 7 | 97,744 | 815 |
| lz4 | 119,851 | 999 | 178,016 | 1,484 |
| lz4hc | 41,969 | 350 | 185,770 | 1,549 |

### Compression — large payload (1,048,576 bytes)

| Transform | encode ops/s | encode MB/s | decode ops/s | decode MB/s |
|---|---:|---:|---:|---:|
| gzip | 34 | 36 | 746 | 782 |
| deflate1 | 225 | 236 | 463 | 486 |
| deflate6 | 33 | 35 | 642 | 673 |
| deflate9 | 19 | 19 | 647 | 678 |
| zstd1 | 591 | 619 | 1,152 | 1,208 |
| zstd3 | 561 | 588 | 1,358 | 1,424 |
| zstd9 | 88 | 92 | 1,461 | 1,532 |
| zstd19 | 5 | 6 | 1,701 | 1,784 |
| lz4 | 718 | 753 | 1,696 | 1,778 |
| lz4hc | 26 | 28 | 2,346 | 2,460 |


### Encodings and checksum (medium payload, 8 KB)

| Transform | encode ops/s | decode ops/s |
|---|---:|---:|
| base64 | 1,788,492 | 1,155,595 |
| base32 | 52,882 | 44,267 |
| hex | 408,117 | 104,802 |
| crc32c | 1,096,045 | 1,160,829 |

### Backends — one small record

| Backend | encode ops/s | decode ops/s |
|---|---:|---:|
| jackson3 | 7,609,018 | 3,442,688 |
| jackson2 | 9,205,690 | 6,961,336 |
| gson | 5,563,157 | 3,556,733 |
| jsonb | 4,226,333 | 1,479,832 |
| fory | 41,404,266 | 42,134,653 |
| protobuf | 74,203,641 | 28,987,859 |

### Backends — an order with 100 line items

| Backend | encode ops/s | decode ops/s |
|---|---:|---:|
| jackson3 | 97,232 | 34,148 |
| jackson2 | 90,301 | 64,292 |
| gson | 57,174 | 39,255 |
| jsonb | 49,420 | 23,331 |
| fory | 1,519,591 | 655,846 |
| protobuf | 524,729 | 349,014 |

### Envelope encryption

| Strategy | Payload | encode ops/s | decode ops/s |
|---|---|---:|---:|
| direct | small | 419,684 | 496,681 |
| bounded | small | 1,015,655 | 499,724 |
| direct | medium | 226,873 | 245,885 |
| bounded | medium | 328,873 | 246,058 |

## Running them yourself

```bash
./mvnw -q -pl codec-benchmarks -am package -DskipTests
java --enable-native-access=ALL-UNNAMED -jar codec-benchmarks/target/benchmarks.jar \
    -rf json -rff results.json
java -cp codec-benchmarks/target/benchmarks.jar org.jwcarman.codec.benchmarks.CompressionRatios
java -cp codec-benchmarks/target/benchmarks.jar org.jwcarman.codec.benchmarks.EncodedSizes
python3 codec-benchmarks/render.py results.json     # the throughput tables
```

Run a subset by name (`CompressionBenchmark`) or parameter (`-p codec=zstd3`).
The module is not published and not part of CI — benchmark numbers from shared
runners are noise.
