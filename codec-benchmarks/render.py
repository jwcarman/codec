#!/usr/bin/env python3
#
# Copyright © 2026 James Carman
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""Render a JMH JSON results file as the Markdown tables used in docs/benchmarks.md.

Usage: render.py results.json
"""
import json
import sys
from collections import defaultdict

SIZES = {"small": 104, "medium": 8338, "large": 1048576}
ORDER = ["gzip", "deflate", "zstd1", "zstd3", "zstd19", "lz4", "lz4hc"]


def load(path):
    rows = defaultdict(dict)
    for r in json.load(open(path)):
        cls, method = r["benchmark"].split(".")[-2:]
        key = (cls, tuple(sorted(r.get("params", {}).items())))
        rows[key][method] = r["primaryMetric"]["score"]
    return rows


def ops(v):
    return f"{v:,.0f}"


def mbps(v, payload):
    return f"{v * SIZES[payload] / 1_000_000:,.0f}"


def main(path):
    rows = load(path)
    print("### Compression\n")
    print("| Transform | Payload | encode ops/s | encode MB/s | decode ops/s | decode MB/s |")
    print("|---|---|---:|---:|---:|---:|")
    for payload in ["small", "medium", "large"]:
        for codec in ORDER:
            r = rows[("CompressionBenchmark", (("codec", codec), ("payload", payload)))]
            print(f"| {codec} | {payload} | {ops(r['encode'])} | {mbps(r['encode'], payload)} "
                  f"| {ops(r['decode'])} | {mbps(r['decode'], payload)} |")
    print("\n### Encodings and checksum (medium payload, 8 KB)\n")
    print("| Transform | encode ops/s | decode ops/s |\n|---|---:|---:|")
    for codec in ["base64", "base32", "hex", "crc32c"]:
        r = rows[("EncodingBenchmark", (("codec", codec),))]
        print(f"| {codec} | {ops(r['encode'])} | {ops(r['decode'])} |")
    print("\n### Backends (one small record)\n")
    print("| Backend | encode ops/s | decode ops/s |\n|---|---:|---:|")
    for backend in ["jackson3", "jackson2", "gson", "jsonb", "fory", "protobuf"]:
        r = rows[("BackendBenchmark", (("backend", backend),))]
        print(f"| {backend} | {ops(r['encode'])} | {ops(r['decode'])} |")
    print("\n### Envelope encryption\n")
    print("| Strategy | Payload | encode ops/s | decode ops/s |\n|---|---|---:|---:|")
    for payload in ["small", "medium"]:
        for strategy in ["direct", "bounded"]:
            r = rows[("CryptoBenchmark", (("payload", payload), ("strategy", strategy)))]
            print(f"| {strategy} | {payload} | {ops(r['encode'])} | {ops(r['decode'])} |")


if __name__ == "__main__":
    main(sys.argv[1])
