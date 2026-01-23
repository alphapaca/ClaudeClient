# LLM Parameter Benchmark

Benchmark script for tuning local LLM parameters with Ollama.

## Setup

```bash
# Install dependency
pip install requests

# Make sure Ollama is running
ollama serve
```

## Quick Start

```bash
# List available models
python benchmark.py --list-models

# Quick benchmark with default model (llama3.2:1b)
python benchmark.py --quick

# Full benchmark
python benchmark.py --model llama3.2:3b
```

## Parameter Testing Examples

### Temperature

```bash
# Test temperature effect on code tasks
python benchmark.py -m qwen2.5:3b -t 0.0 0.1 0.3 0.5 0.7 1.0 --tasks json_extraction complete_function
```

### Context Window

```bash
# Test context handling
python benchmark.py -m phi3:mini -c 512 1024 2048 4096 --tasks summarize_code
```

### Max Tokens

```bash
# Test output length limits
python benchmark.py -m gemma2:2b --max-tokens 64 128 256 512
```

### Compare Quantizations

```bash
# Run same benchmark with different quantized models
python benchmark.py -m llama3.2:3b-q4_0 -q Q4_0 --quick
python benchmark.py -m llama3.2:3b-q8_0 -q Q8_0 --quick
python benchmark.py -m llama3.2:3b-fp16 -q FP16 --quick
```

## All Options

```
--model, -m         Model name (default: llama3.2:1b)
--quantization, -q  Label for quantization (for logging)
--temperatures, -t  Temperature values (default: 0.0 0.3 0.7 1.0)
--max-tokens        Max token values (default: 128 256)
--contexts, -c      Context sizes (default: 1024 2048)
--base-url          Ollama URL (default: http://localhost:11434)
--output, -o        Results directory (default: ./results)
--quick             Quick mode (fewer combinations)
--tasks             Run specific tasks by name
--list-models       List available models
```

## Available Tasks

| Name | Category | What it tests |
|------|----------|---------------|
| complete_function | code_completion | Kotlin factorial function |
| complete_list_filter | code_completion | Python list filtering |
| fix_off_by_one | code_fix | Index error fix |
| fix_null_check | code_fix | Kotlin null safety |
| explain_recursion | explanation | Code explanation |
| math_word_problem | reasoning | Math calculation |
| logic_puzzle | reasoning | Syllogism |
| json_extraction | structured | JSON output (test temp=0) |
| summarize_code | context | Longer context handling |

## Output

Results are saved to `./results/` as:
- `benchmark_YYYYMMDD_HHMMSS.csv` - Spreadsheet format
- `benchmark_YYYYMMDD_HHMMSS.json` - Full responses

## Recommended Small Models

| Model | Size | Strengths |
|-------|------|-----------|
| qwen2.5:3b | 3B | Best overall |
| phi3:mini | 3.8B | Reasoning |
| gemma2:2b | 2B | Instruction following |
| llama3.2:1b | 1B | Fastest |
| llama3.2:3b | 3B | Good balance |

## Parameter Tuning Guide

### Temperature
- `0.0` - Deterministic, best for JSON/code syntax
- `0.1-0.3` - Low variance, good for code completion
- `0.5-0.7` - Balanced, good for explanations
- `0.8-1.0` - Creative, more varied outputs

### Context Window (num_ctx)
- Larger = more memory, slower inference
- Start with 2048, increase if needed for long inputs
- Watch for quality degradation at model's max context

### Max Tokens
- Match to expected output length
- Too low = truncated responses
- Too high = wasted compute, potential rambling

### Quantization
- Q4_K_M - Good balance of size/quality
- Q5_K_M - Better quality, slightly larger
- Q8_0 - Near full quality
- FP16 - Full precision (baseline)
