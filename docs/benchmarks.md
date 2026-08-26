# Benchmarks

Numbers from the `codec-benchmarks` module (JMH), so the guides' speed and
size claims are reproducible rather than folklore. **One machine, one run:**
Apple M4 Max, 64 GB, OpenJDK 25.0.3, codec 0.8.0-SNAPSHOT at `b44c2a8`,
2026-08-26. Throughput mode, 1 fork, 3 × 1 s warm-up, 5 × 1 s measurement.
The raw JMH output is in `codec-benchmarks/results/`; treat the relative
ordering as the finding, not the absolute figures.

Payloads: **small** is a 104-byte JSON record, **medium** an 8 KB JSON
document, **large** 1 MB of pseudo-English prose.

## What the numbers say

- **For medium and large payloads, zstd and LZ4 are in a different league
  from gzip.** zstd level 3 encodes 1 MB at ~630 MB/s against gzip's ~38 MB/s
  and decodes twice as fast; LZ4 encodes at ~800 MB/s and decodes at ~1.9 GB/s.
- **For tiny payloads the JDK wins.** At 104 bytes gzip and deflate out-run
  zstd and LZ4 on both encode and decode — the fixed cost of a JNI stream
  dominates, and there is nothing to compress anyway (every transform *grows*
  the small payload).
- **Ratio is not zstd's advantage at the default level.** On the prose payload
  zstd 3 lands at 21.7% of the input against gzip's 19.2%; only zstd 19 (16.2%)
  beats gzip, and it encodes at 6 MB/s — archival territory.
- **LZ4-HC is a decode-side optimisation.** It compresses ~25× slower than
  plain LZ4 (slower than gzip) for a better ratio, and decodes fastest of
  anything measured. Use it for write-rarely, read-constantly data.
- **The text encodings are negligible next to any backend or transform**, with
  the pure-Java Base32 the slowest of them (~440 MB/s).
- **Binary backends are 5–10× faster than JSON** for a small record; Fory
  and Protobuf both exceed 29 M ops/s on decode. Among the JSON backends
  Jackson 2 is the fastest in this run, JSON-B (Yasson) the slowest on decode.
- **`BoundedDataKeyStrategy` doubles encrypt throughput on small payloads**
  (1.0 M vs 0.42 M ops/s) by amortising the KEK wrap; decode is unchanged,
  since every message is unwrapped on its own.

## Compression ratios

Compressed size and percentage of the input, from `CompressionRatios`:

| Transform | small (104 B) | medium (8,338 B) | large (1,048,576 B) |
|---|---|---|---|
| gzip | 115 (110.6%) | 1,647 (19.8%) | 201,172 (19.2%) |
| deflate | 103 (99.0%) | 1,635 (19.6%) | 201,160 (19.2%) |
| zstd1 | 102 (98.1%) | 1,614 (19.4%) | 242,999 (23.2%) |
| zstd3 | 102 (98.1%) | 1,590 (19.1%) | 227,860 (21.7%) |
| zstd19 | 103 (99.0%) | 1,467 (17.6%) | 169,684 (16.2%) |
| lz4 | 123 (118.3%) | 2,704 (32.4%) | 491,989 (46.9%) |
| lz4hc | 123 (118.3%) | 2,335 (28.0%) | 282,916 (27.0%) |

## Throughput

### Compression

| Transform | Payload | encode ops/s | encode MB/s | decode ops/s | decode MB/s |
|---|---|---:|---:|---:|---:|
| gzip | small | 265,010 | 28 | 588,173 | 61 |
| deflate | small | 266,913 | 28 | 604,338 | 63 |
| zstd1 | small | 170,339 | 18 | 182,783 | 19 |
| zstd3 | small | 108,011 | 11 | 197,444 | 21 |
| zstd19 | small | 3,013 | 0 | 190,908 | 20 |
| lz4 | small | 430,828 | 45 | 527,161 | 55 |
| lz4hc | small | 287,267 | 30 | 533,725 | 56 |
| gzip | medium | 26,339 | 220 | 116,634 | 972 |
| deflate | medium | 25,427 | 212 | 102,425 | 854 |
| zstd1 | medium | 66,164 | 552 | 99,350 | 828 |
| zstd3 | medium | 44,243 | 369 | 85,965 | 717 |
| zstd19 | medium | 872 | 7 | 92,225 | 769 |
| lz4 | medium | 127,963 | 1,067 | 186,417 | 1,554 |
| lz4hc | medium | 42,953 | 358 | 194,756 | 1,624 |
| gzip | large | 36 | 38 | 724 | 759 |
| deflate | large | 36 | 38 | 652 | 684 |
| zstd1 | large | 628 | 658 | 1,241 | 1,301 |
| zstd3 | large | 600 | 629 | 1,407 | 1,476 |
| zstd19 | large | 6 | 6 | 1,719 | 1,803 |
| lz4 | large | 770 | 808 | 1,811 | 1,899 |
| lz4hc | large | 27 | 29 | 2,451 | 2,570 |

### Encodings and checksum (medium payload, 8 KB)

| Transform | encode ops/s | decode ops/s |
|---|---:|---:|
| base64 | 1,788,492 | 1,155,595 |
| base32 | 52,882 | 44,267 |
| hex | 408,117 | 104,802 |
| crc32c | 1,096,045 | 1,160,829 |

### Backends (one small record)

| Backend | encode ops/s | decode ops/s |
|---|---:|---:|
| jackson3 | 6,839,472 | 3,624,794 |
| jackson2 | 9,163,029 | 6,959,694 |
| gson | 5,520,695 | 3,744,129 |
| jsonb | 4,585,783 | 1,604,356 |
| fory | 38,870,762 | 42,057,050 |
| protobuf | 74,527,336 | 29,444,696 |

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
python3 codec-benchmarks/render.py results.json     # the tables above
```

Run a subset by name (`CompressionBenchmark`) or parameter (`-p codec=zstd3`).
The module is not published and not part of CI — benchmark numbers from shared
runners are noise.
