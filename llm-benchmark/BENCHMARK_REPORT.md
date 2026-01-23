# Local LLM Parameter Benchmark Report

**Date:** January 23, 2026
**Platform:** macOS Darwin 24.6.0 (Apple Silicon)
**Ollama Version:** Latest

## Executive Summary

This report presents benchmark results for tuning local LLM parameters across three models. Key findings:

- **Best Overall Model:** Mistral 7B (highest quality, 3.5 t/s average)
- **Best Small Model:** Llama 3.2 3B (good balance of speed and quality)
- **Optimal Temperature:** 0.0-0.3 for code tasks, 0.7 for explanations
- **Optimal Max Tokens:** 256+ for code tasks (avoid truncation)
- **Context Window:** 1024-2048 works well; 4096 causes timeouts on this hardware

---

## Models Tested

| Model | Size | Quantization | Avg Speed |
|-------|------|--------------|-----------|
| llama3.2:latest | 3B | Q4_K_M | 6.3 t/s |
| llama3:latest | 8B | Q4_K_M | 1.8 t/s |
| mistral:latest | 7B | Q4_K_M | 3.5 t/s |

---

## 1. Temperature Analysis

### Summary by Temperature

| Temp | Avg Score | Best Use Case |
|------|-----------|---------------|
| 0.0 | 88.9% | JSON extraction, deterministic code |
| 0.1 | 100% | Code completion (best) |
| 0.2 | 66.7% | - |
| 0.3 | 83.3% | General purpose |
| 0.5 | 91.7% | Balanced tasks |
| 0.7 | 86.1% | Explanations, creative |
| 1.0 | 75.0% | Too random for code |

### Task-Specific Temperature Recommendations

#### Code Completion (Kotlin factorial)
```
temp=0.0: 100% score - Produces clean recursive solution
temp=0.1: 100% score - Equally good, slightly faster
temp=0.3: 100% score - Still reliable
temp=0.7:  75% score - Sometimes uses iterative approach (loses "factorial" keyword)
temp=1.0:  75% score - More variation, occasional errors
```

**Recommendation:** Use **temp=0.0 to 0.3** for code completion

#### JSON Extraction
```
temp=0.0: 100% - Perfect: {"name": "John Smith", "age": 32, "city": "Tokyo"}
temp=0.1: 100% - Perfect
temp=0.3: 100% - Perfect
temp=0.5: 100% - Perfect
temp=0.7: 100% - Perfect
temp=1.0: 100% - Perfect (robust task)
```

**Recommendation:** Temperature doesn't matter for simple JSON extraction - model is consistent

#### Bug Fixing (off-by-one error)
```
temp=0.0: 100% - Correctly identifies len(arr)-1 fix
temp=0.1: 100% - Good explanation
temp=0.2: 100% - Good
temp=0.3:  50% - Explains bug but truncates before showing fix
temp=0.7:  50% - More verbose, sometimes misses the fix
temp=1.0:  50% - Too much explanation, not enough solution
```

**Recommendation:** Use **temp=0.0 to 0.2** for bug fixing

---

## 2. Model Comparison

### Overall Scores by Category

| Category | Llama 3.2 (3B) | Llama 3 (8B) | Mistral (7B) |
|----------|----------------|--------------|--------------|
| Code Completion | 93.8% | 10%* | **100%** |
| Code Fix | 81.2% | 0%* | 62.5% |
| Explanation | **100%** | 50%* | **100%** |
| Reasoning | 87.5% | 87.5% | 81.2% |
| JSON Structured | **100%** | **100%** | **100%** |
| Context/Summary | 62.5% | 75% | **75%** |

*Llama 3 (8B) had timeout issues during testing due to resource constraints

### Speed Comparison

| Model | Avg Tokens/sec | Time for 100 tokens |
|-------|----------------|---------------------|
| llama3.2:latest | 6.3 t/s | ~16 seconds |
| llama3:latest | 1.8 t/s | ~56 seconds |
| mistral:latest | 3.5 t/s | ~29 seconds |

### Sample Outputs Comparison

**Task:** Complete Kotlin factorial function

**Llama 3.2 (temp=0.0):**
```kotlin
fun factorial(n: Int): Long {
    return if (n == 0) {
        1L
    } else {
        n.toLong() * factorial(n - 1)
    }
}
```

**Mistral (temp=0.0):**
```kotlin
fun factorial(n: Int): Long {
    if (n <= 1) return 1L
    return n * factorial(n - 1)
}
```
*Note: Mistral provides additional explanation with the code*

---

## 3. Max Tokens Analysis

### Impact on Task Completion

| max_tokens | Code Completion | Bug Fix | Explanation |
|------------|-----------------|---------|-------------|
| 64 | 75% (truncated) | 0% (truncated) | 100% |
| 128 | 100% | 50% (truncated) | 100% |
| 256 | 100% | 100% | 100% |
| 512 | 100% | 100% | 100% |

### Speed vs Max Tokens

| max_tokens | Tokens/sec | Notes |
|------------|------------|-------|
| 64 | 14.2 t/s | Fast but truncates |
| 128 | 7.8 t/s | Good for short tasks |
| 256 | 8.5 t/s | **Optimal for most tasks** |
| 512 | 9.4 t/s | No benefit over 256 |

### Key Finding

**max_tokens=64** causes truncation in bug fix explanations:
```
"**Bug Explanation**

The bug in this code is that it attempts to access an element at the
index equal to the length of the array, which is out of bounds. In
Python (and most programming languages), indices start from 0, so the
last valid index for a list with `n` elements is `n"  <-- TRUNCATED
```

**Recommendation:** Use **max_tokens=256** as the default. Use 128 for simple Q&A, 512+ for complex explanations.

---

## 4. Context Window Analysis

### Results by Context Size (num_ctx)

| num_ctx | Success Rate | Speed | Notes |
|---------|--------------|-------|-------|
| 512 | 75% | 0.4 t/s | Works but slow |
| 1024 | 100% | 0.8 t/s | **Best balance** |
| 2048 | 75% | 0.5 t/s | Default, reliable |
| 4096 | 0% | timeout | Too large for hardware |

### Memory/Speed Tradeoff

Larger context windows require more memory and slow down inference. On this Apple Silicon Mac:

- **512-1024:** Works well for short prompts
- **2048:** Good default for most tasks
- **4096+:** Causes timeouts; requires more powerful hardware

### Recommendation

Start with **num_ctx=2048** and reduce to 1024 if speed is critical. Only increase beyond 2048 if you have sufficient RAM and the task requires it.

---

## 5. Recommended Configurations

### For Code Completion
```python
{
    "temperature": 0.1,
    "max_tokens": 256,
    "num_ctx": 2048
}
```

### For Bug Fixing
```python
{
    "temperature": 0.0,
    "max_tokens": 256,
    "num_ctx": 2048
}
```

### For JSON Extraction
```python
{
    "temperature": 0.0,
    "max_tokens": 128,
    "num_ctx": 1024
}
```

### For Explanations
```python
{
    "temperature": 0.7,
    "max_tokens": 256,
    "num_ctx": 2048
}
```

### For Math/Reasoning
```python
{
    "temperature": 0.0,
    "max_tokens": 256,
    "num_ctx": 2048
}
```

---

## 6. Model Selection Guide

| Use Case | Recommended Model | Why |
|----------|-------------------|-----|
| Fast prototyping | llama3.2:latest | 6+ t/s, good quality |
| Production code gen | mistral:latest | Best code quality |
| Resource constrained | llama3.2:latest | Smallest, fastest |
| Complex reasoning | mistral:latest | Better explanations |
| JSON/structured | Any | All perform equally |

---

## 7. Quality Examples

### JSON Extraction (All models perfect)

**Input:** "John Smith is 32 years old and lives in Tokyo."

**Output (all temps, all models):**
```json
{"name": "John Smith", "age": 32, "city": "Tokyo"}
```

### Logic Puzzle (All models correct)

**Input:** "If all Bloops are Razzies, and all Razzies are Lazzies, are all Bloops Lazzies?"

**Output:**
> Yes, because if all Bloops are Razzies and all Razzies are Lazzies, then by transitive property, all Bloops are Lazzies.

### Fibonacci Explanation

**Llama 3.2:**
> This code defines a recursive function called `mystery` that calculates the nth Fibonacci number, where each number is the sum of the two preceding ones. The base case for the recursion is when `n` is 0 or 1, in which case it returns `n`.

**Mistral:**
> This Python function, named `mystery`, is a recursive implementation of the Fibonacci sequence. It calculates the nth number in the Fibonacci sequence. If `n` is less than or equal to 1, it returns `n`. Otherwise, it calls itself twice with arguments `n-1` and `n-2`, and adds their results together.

---

## 8. Quantization Notes

All tests used **Q4_K_M** quantization. Based on the results:

- Q4_K_M provides good quality for code tasks
- No significant errors observed from quantization
- Math problems solved correctly (7 apples at $2 with 20% off = $11.20)
- JSON extraction 100% accurate

For testing different quantizations, pull models with specific tags:
```bash
ollama pull llama3.2:3b-q4_0
ollama pull llama3.2:3b-q8_0
ollama pull llama3.2:3b-fp16
```

---

## Appendix: Raw Results Location

All detailed results saved in:
- `results/benchmark_*.csv` - Spreadsheet format
- `results/benchmark_*.json` - Full responses

---

## Conclusions

1. **Temperature 0.0-0.3** is best for deterministic code tasks
2. **Temperature 0.7** works better for creative/explanatory tasks
3. **max_tokens=256** is the sweet spot for most tasks
4. **num_ctx=2048** is a safe default; reduce for speed, increase for long contexts
5. **Llama 3.2 (3B)** offers the best speed/quality tradeoff for local inference
6. **Mistral (7B)** produces higher quality code but runs slower
7. **Q4_K_M quantization** maintains good quality for coding tasks
