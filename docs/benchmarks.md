# Benchmarks

Numbers from the `codec-benchmarks` module (JMH), so the guides' speed and
size claims are reproducible rather than folklore. **One machine, one run:**
Apple M4 Max, 64 GB, OpenJDK 25.0.3, codec 0.8.0-SNAPSHOT, 2026-09-06.
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
  from the JDK.** zstd 3 encodes 1 MB at ~620 MB/s against deflate 6's ~38 MB/s
  and decodes two and a half times as fast; LZ4 encodes at ~790 MB/s and
  decodes at ~1.9 GB/s. Even the JDK's fastest level (deflate 1, ~250 MB/s) is
  well behind zstd 1 (~650 MB/s) while producing a worse ratio (26.0% vs 23.2%).
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
- **LZ4-HC is a decode-side optimisation.** It compresses ~22× slower than
  plain LZ4 (about gzip's speed) for a better ratio, and decodes fastest of
  anything measured. Use it for write-rarely, read-constantly data.
- **The text encodings are negligible next to any backend or transform**, with
  the pure-Java Base32 the slowest of them (~440 MB/s).
- **Binary backends pull further ahead as the payload grows.** On the small
  record Fory decodes ~5× faster than Jackson 2; on the 100-item order it is
  ~10× faster on decode and ~15× on encode (1.4 M orders/s), and Protobuf is
  ~5× faster than the JSON backends. Their output is also half the size:
  4.5 KB (Fory) and 4.8 KB (Protobuf) against 9.1 KB of JSON.
- **Fory's compatible mode costs bytes only on tiny payloads.** `codec-fory`
  takes Fory's default, compatible mode since Fory 1.2.0 (see the
  [Fory guide](guides/fory.md#schema-evolution)), which writes each class's
  schema once per message: the lone four-field record
  is 74 bytes, no smaller than its 72 bytes of JSON, while the 100-item order
  pays 2%. Throughput is unaffected.
- **Among the JSON backends, Jackson 2 decodes fastest** in this run — twice
  as fast as Jackson 3 on the order — and JSON-B (Yasson) is the slowest at
  both sizes.
- **`BoundedDataKeyStrategy` doubles encrypt throughput on small payloads**
  (1.0 M vs 0.42 M ops/s) by amortising the KEK wrap; decode is unchanged,
  since every message is unwrapped on its own.

## Encoded sizes by backend

From `EncodedSizes`:

| Backend | small (bytes) | medium (bytes) |
|---|---:|---:|
| jackson3 | 72 | 9,096 |
| jackson2 | 72 | 9,096 |
| gson | 72 | 9,096 |
| jsonb | 72 | 9,096 |
| fory | 74 | 4,510 |
| protobuf | 35 | 4,815 |

## Throughput

Compression tables carry the compressed size and ratio beside the speed, so
the level trade-offs read in one place.

### Compression — small payload (104 bytes)

| Transform | compressed bytes | of input | encode MB/s | decode MB/s | encode ops/s | decode ops/s |
|---|---:|---:|---:|---:|---:|---:|
| gzip | 115 | 110.6% | 27 | 61 | 263,438 | 584,761 |
| deflate1 | 103 | 99.0% | 27 | 63 | 255,848 | 608,249 |
| deflate6 | 103 | 99.0% | 26 | 63 | 252,615 | 604,734 |
| deflate9 | 103 | 99.0% | 27 | 63 | 255,637 | 608,106 |
| zstd1 | 102 | 98.1% | 17 | 21 | 161,182 | 204,964 |
| zstd3 | 102 | 98.1% | 11 | 17 | 102,589 | 162,277 |
| zstd9 | 102 | 98.1% | 2 | 17 | 20,516 | 167,031 |
| zstd19 | 103 | 99.0% | 0 | 16 | 2,991 | 154,408 |
| lz4 | 123 | 118.3% | 41 | 49 | 392,272 | 468,573 |
| lz4hc | 123 | 118.3% | 29 | 50 | 278,798 | 483,448 |

### Compression — medium payload (8,338 bytes)

| Transform | compressed bytes | of input | encode MB/s | decode MB/s | encode ops/s | decode ops/s |
|---|---:|---:|---:|---:|---:|---:|
| gzip | 1,647 | 19.8% | 218 | 967 | 26,098 | 115,925 |
| deflate1 | 1,846 | 22.1% | 496 | 808 | 59,532 | 96,957 |
| deflate6 | 1,635 | 19.6% | 212 | 853 | 25,413 | 102,297 |
| deflate9 | 1,606 | 19.3% | 159 | 854 | 19,080 | 102,468 |
| zstd1 | 1,614 | 19.4% | 561 | 692 | 67,264 | 83,003 |
| zstd3 | 1,590 | 19.1% | 377 | 772 | 45,171 | 92,633 |
| zstd9 | 1,508 | 18.1% | 97 | 897 | 11,590 | 107,549 |
| zstd19 | 1,467 | 17.6% | 7 | 889 | 881 | 106,610 |
| lz4 | 2,704 | 32.4% | 1,052 | 1,532 | 126,206 | 183,791 |
| lz4hc | 2,335 | 28.0% | 381 | 1,622 | 45,741 | 194,508 |

### Compression — large payload (1,048,576 bytes)

| Transform | compressed bytes | of input | encode MB/s | decode MB/s | encode ops/s | decode ops/s |
|---|---:|---:|---:|---:|---:|---:|
| gzip | 201,172 | 19.2% | 38 | 756 | 36 | 721 |
| deflate1 | 273,057 | 26.0% | 248 | 495 | 236 | 472 |
| deflate6 | 201,160 | 19.2% | 38 | 677 | 36 | 646 |
| deflate9 | 197,182 | 18.8% | 20 | 700 | 20 | 667 |
| zstd1 | 242,999 | 23.2% | 648 | 1,561 | 618 | 1,488 |
| zstd3 | 227,860 | 21.7% | 621 | 1,755 | 593 | 1,673 |
| zstd9 | 222,392 | 21.2% | 95 | 1,835 | 91 | 1,750 |
| zstd19 | 169,684 | 16.2% | 6 | 2,113 | 6 | 2,015 |
| lz4 | 491,989 | 46.9% | 792 | 1,886 | 755 | 1,799 |
| lz4hc | 282,916 | 27.0% | 36 | 2,549 | 35 | 2,431 |


### Encodings and checksum (medium payload, 8 KB)

| Transform | encode ops/s | decode ops/s |
|---|---:|---:|
| base64 | 1,787,991 | 1,148,057 |
| base32 | 52,531 | 48,224 |
| hex | 406,702 | 104,500 |
| crc32c | 1,091,752 | 1,160,519 |

### Backends — one small record

| Backend | encode ops/s | decode ops/s |
|---|---:|---:|
| jackson3 | 7,826,665 | 3,575,335 |
| jackson2 | 8,874,575 | 6,998,820 |
| gson | 5,550,844 | 3,832,645 |
| jsonb | 4,099,414 | 1,552,030 |
| fory | 43,554,798 | 36,590,914 |
| protobuf | 73,836,647 | 28,808,969 |

### Backends — an order with 100 line items

| Backend | encode ops/s | decode ops/s |
|---|---:|---:|
| jackson3 | 97,993 | 35,863 |
| jackson2 | 90,473 | 64,103 |
| gson | 56,377 | 40,996 |
| jsonb | 49,764 | 23,859 |
| fory | 1,385,881 | 674,915 |
| protobuf | 524,202 | 355,915 |

### Envelope encryption

| Strategy | Payload | encode ops/s | decode ops/s |
|---|---|---:|---:|
| direct | small | 415,616 | 494,701 |
| bounded | small | 1,017,118 | 501,934 |
| direct | medium | 229,782 | 245,527 |
| bounded | medium | 329,200 | 245,931 |

## Running them yourself

```bash
./mvnw -q -pl codec-benchmarks -am package -DskipTests
java --enable-native-access=ALL-UNNAMED -jar codec-benchmarks/target/benchmarks.jar \
    -rf json -rff results.json
java -cp codec-benchmarks/target/benchmarks.jar org.jwcarman.codec.benchmarks.CompressionRatios --json > ratios.json
java -cp codec-benchmarks/target/benchmarks.jar org.jwcarman.codec.benchmarks.EncodedSizes
python3 codec-benchmarks/render.py results.json ratios.json     # the throughput tables
```

Run a subset by name (`CompressionBenchmark`) or parameter (`-p codec=zstd3`).
The module is not published and not part of CI — benchmark numbers from shared
runners are noise.
