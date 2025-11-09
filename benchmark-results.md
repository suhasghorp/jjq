# JQ vs JJQ Performance Comparison Report

**Generated:** 2025-11-09 18:09:46

## Environment

- **jq Version:** jq-1.8.1
- **jjq Version:** 1.0-SNAPSHOT
- **Java Version:** java : openjdk version "25.0.1" 2025-10-21
- **OS:** Microsoft Windows NT 10.0.19045.0
- **Native Image:** Available

---
## Functional Correctness Results

| # | Test | Query | Result |
|---|------|-------|--------|
| 1 | Identity (semantic) | `.` | âœ… PASS |
| 2 | Top-level field (semantic) | `.data` | âœ… PASS |
| 3 | Piped field access | `.data | .testId` | âœ… PASS |
| 4 | Deep nested access | `.data | .median | .firstView | .SpeedIndex` | âœ… PASS |
| 5 | Object field (semantic) | `.data | .runs` | âœ… PASS |
| 6 | Nested object access | `.data | .median` | âœ… PASS |
| 7 | Deep object access | `.data | .median | .firstView` | âœ… PASS |
| 8 | .N syntax quirk (jq:0.1, jjq:field '1') | `.data | .runs | .1` | âŒ FAIL |

**Summary:** 7 / 8 tests passed (87.5%)

---

## Performance Results

| Test | jq (ms) | jjq JAR (ms) | jjq Native (ms) | JAR vs jq | Native vs jq |
|------|---------|--------------|-----------------|-----------|--------------|
| Identity Query | 28.88 | 607.92 | 36.05 | 21.05x slower | 1.25x slower |
| Simple Field Access | 28.27 | 517.78 | 34.09 | 18.32x slower | 1.21x slower |
| Deep Nested Access | 28.13 | 526.93 | 34.8 | 18.73x slower | 1.24x slower |
| Object Field Access | 29.4 | 635.85 | 35.47 | 21.63x slower | 1.21x slower |
| Complex Pipeline | 43.46 | 526.3 | 34.02 | 12.11x slower | 1.28x faster |
| Batch Processing (20 files) | 560.63 | N/A | 680.98 | N/A | 1.21x slower |

---

## Detailed Query Information

### Identity Query
- **Query:** `.`
- **Iterations:** 100

### Simple Field Access
- **Query:** `.data | .testId`
- **Iterations:** 100

### Deep Nested Access
- **Query:** `.data | .median | .firstView | .SpeedIndex`
- **Iterations:** 100

### Object Field Access
- **Query:** `.data | .runs | .1`
- **Iterations:** 50

### Complex Pipeline
- **Query:** `.data | .runs | .1 | .firstView | .SpeedIndex`
- **Iterations:** 50

### Batch Processing (20 files)
- **Query:** `.data | .testId`
- **Iterations:** 20

---

## Notes

- All times are averaged over multiple iterations
- Warmup rounds were performed before measurements
- Tests use WebPageTest.org JSON data (sample and synthetic)
- Baseline measurements (before optimizations)

