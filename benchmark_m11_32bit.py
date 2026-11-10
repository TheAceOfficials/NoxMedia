#!/usr/bin/env python3
"""
32-bit Samsung M11 Streaming Benchmark Suite.
Tests Cold Start TTFB, Seek TTFB, and 10-Second Continuous Playback Flow across all 6 test files.

Usage:
    python3 benchmark_m11_32bit.py [HOST:PORT]
    python3 benchmark_m11_32bit.py 192.168.1.49:7788
"""

import sys
import time
import json
import urllib.request

DEFAULT_HOST = "192.168.1.49:7788"
TARGET_HOST = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_HOST

FILES = [
    {
        "name": "Lanterns S01E01 (2160p 4K)",
        "quality": "2160p 4K (Split)",
        "size_gb": 7.95,
        "duration_min": 50,
        "seek_min": 10,
        "path": "/stream/video/-1003941047756/tg_-1003941047756_2742_split/Lanterns%20S01E01%202160p%20AMZN%20WEB-DL%20Multi.DDP5.1.Atmos%20DV%20HDR%20H265%20MomStealer.mkv"
    },
    {
        "name": "Lucifer S01E01 (1080p)",
        "quality": "1080p BluRay (Single)",
        "size_gb": 1.43,
        "duration_min": 45,
        "seek_min": 10,
        "path": "/stream/video/-1003941047756/tg_-1003941047756_846/Lucifer%20%282016%29%20S01E01%201080p%2010bit%20BluRay%20HEVC%20%5BHindi%20DDP%205.1%20%2B%20English%20AAC%205.1%5D%20H.265%20%28Vyndros-4kHDHub.com%29.mkv"
    },
    {
        "name": "Akame Ga Kill (1080p Anime)",
        "quality": "1080p Anime (Single)",
        "size_gb": 0.44,
        "duration_min": 24,
        "seek_min": 8,
        "path": "/stream/video/-1003941047756/tg_-1003941047756_2593/Akame.Ga.Kill.English.Subtitled.S01E01.Kill.the.Darkness.1080p.AMZN.WEB-DL.English.DDP2.0-Japanese.DDP2.0.H.265-4kHdHub.Com%7C%20MomStealer.mkv"
    },
    {
        "name": "Spider-Man Far From Home (2160p 4K)",
        "quality": "2160p 4K HDR (Split)",
        "size_gb": 6.38,
        "duration_min": 129,
        "seek_min": 50,
        "path": "/stream/video/-1003941047756/tg_-1003941047756_6164_split/Spider-Man.Far.from.Home.2019.2160p.10bit.HDR.BluRay.x265.%5BHindi%20DD5.1%2BEnglish%20Dolby%20TrueHD%20Atmos%207.1%5D.mkv"
    },
    {
        "name": "Shang-Chi IMAX (2160p 4K)",
        "quality": "2160p 4K IMAX (Split)",
        "size_gb": 20.09,
        "duration_min": 132,
        "seek_min": 50,
        "path": "/stream/video/-1003941047756/tg_-1003941047756_2123_split/Shang-Chi%20and%20the%20Legend%20of%20the%20Ten%20Rings%20%282021%29%20IMAX%202160p%20DSNP%20WEB-DL%20DV%20HDR%2010bit%20HEVC%20%5BHindi%20DDP%205.1%20%2B%20English%20TrueHD%20Atmos%207.1%5D%20x265%20%28MZABI-LUMiX%29%204KHDHub.com_%20TheAceOfficials.mkv"
    },
    {
        "name": "Dunkirk IMAX REMUX (2160p 4K)",
        "quality": "2160p 4K REMUX (26 Parts)",
        "size_gb": 51.31,
        "duration_min": 106,
        "seek_min": 50,
        "path": "/stream/video/-1003941047756/tg_-1003941047756_935_split/Dunkirk.2017.IMAX.2160p.UHD.BluRay.REMUX.DV.HDR10Plus.HEVC.TrueHD.7.1.96K-WiLDCAT.mkv"
    }
]

def run():
    print(f"\n=======================================================")
    print(f"🚀 Running Samsung M11 (32-bit) Streaming Benchmark")
    print(f"Target Server: http://{TARGET_HOST}")
    print(f"=======================================================\n")

    results = []
    for item in FILES:
        url = f"http://{TARGET_HOST}{item['path']}"
        req_bitrate = (item["size_gb"] * 1024) / (item["duration_min"] * 60)
        print(f"--> Testing: {item['name']} ({item['size_gb']} GB)")

        # 1. Cold Start
        req0 = urllib.request.Request(url, headers={"Range": "bytes=0-3145727"})
        t0 = time.time()
        start_ttfb = None
        bytes0 = 0
        try:
            with urllib.request.urlopen(req0, timeout=25) as resp:
                while True:
                    c = resp.read(65536)
                    if not c:
                        break
                    if start_ttfb is None:
                        start_ttfb = time.time() - t0
                    bytes0 += len(c)
            dur0 = max(0.001, time.time() - t0)
            speed0 = (bytes0 / (1024 * 1024)) / dur0
        except Exception as e:
            start_ttfb = 0
            speed0 = 0

        time.sleep(1.0)

        # 2. Seek + Continuous 10s Playback
        total_bytes = int(item["size_gb"] * 1024 * 1024 * 1024)
        offset = int(((item["seek_min"] / item["duration_min"]) * total_bytes) // 1048576) * 1048576
        req_seek = urllib.request.Request(url, headers={"Range": f"bytes={offset}-"})
        t_seek0 = time.time()
        seek_ttfb = None
        stream_start = None
        last_chunk_time = None
        bytes_streamed = 0
        max_gap_ms = 0.0

        try:
            with urllib.request.urlopen(req_seek, timeout=30) as resp:
                while True:
                    now = time.time()
                    if stream_start is not None and (now - stream_start) >= 10.0:
                        break
                    c = resp.read(65536)
                    if not c:
                        break
                    now_read = time.time()
                    if seek_ttfb is None:
                        seek_ttfb = now_read - t_seek0
                        stream_start = now_read
                        last_chunk_time = now_read
                    else:
                        gap = (now_read - last_chunk_time) * 1000.0
                        if gap > max_gap_ms:
                            max_gap_ms = gap
                        last_chunk_time = now_read
                    bytes_streamed += len(c)

            actual_dur = max(0.001, time.time() - stream_start) if stream_start else 0.001
            cont_speed = (bytes_streamed / (1024 * 1024)) / actual_dur
            verdict = "SMOOTH" if cont_speed >= req_bitrate else "BUFFER RISK"
        except Exception as e:
            seek_ttfb = 0
            cont_speed = 0
            verdict = f"ERROR: {e}"

        results.append({
            "name": item["name"],
            "size": item["size_gb"],
            "req_bitrate": req_bitrate,
            "start_ttfb": start_ttfb,
            "seek_ttfb": seek_ttfb,
            "cont_speed": cont_speed,
            "max_gap_ms": max_gap_ms,
            "verdict": verdict
        })
        print(f"    Done: Start={start_ttfb:.2f}s, Seek={seek_ttfb:.2f}s, Speed={cont_speed:.2f}MB/s ({verdict})\n")
        time.sleep(1.0)

    print("\n" + "=" * 80)
    print("FINAL BENCHMARK TABLE")
    print("=" * 80)
    print(f"{'Media Title':<30} | {'Size':<8} | {'Req MB/s':<9} | {'Seek TTFB':<9} | {'Speed MB/s':<10} | {'Verdict'}")
    print("-" * 80)
    for r in results:
        print(f"{r['name']:<30} | {r['size']:<8.2f} | {r['req_bitrate']:<9.2f} | {r['seek_ttfb']:<9.2f} | {r['cont_speed']:<10.2f} | {r['verdict']}")
    print("=" * 80 + "\n")

if __name__ == "__main__":
    run()

