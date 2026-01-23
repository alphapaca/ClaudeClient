#!/usr/bin/env python3
"""
Local LLM Parameter Benchmark Script
Tests temperature, max_tokens, context handling, and measures quality/speed.
Works with Ollama API.
"""

import json
import time
import requests
import argparse
from dataclasses import dataclass
from typing import Optional
import csv
from datetime import datetime
from pathlib import Path


OLLAMA_BASE_URL = "http://localhost:11434"


@dataclass
class BenchmarkTask:
    name: str
    category: str  # code_completion, code_fix, explanation, reasoning, creative
    prompt: str
    expected_contains: list[str]  # Strings that should appear in good responses
    context_size: int  # Approximate tokens in prompt
    ideal_max_tokens: int


# Benchmark tasks covering different capabilities
TASKS = [
    # === CODE COMPLETION ===
    BenchmarkTask(
        name="complete_function",
        category="code_completion",
        prompt="""Complete this Kotlin function that calculates factorial:

```kotlin
fun factorial(n: Int): Long {
    // TODO: implement
```

Return only the completed function.""",
        expected_contains=["if", "return", "factorial", "*"],
        context_size=50,
        ideal_max_tokens=150,
    ),

    BenchmarkTask(
        name="complete_list_filter",
        category="code_completion",
        prompt="""Complete this Python function:

```python
def filter_even_numbers(numbers: list[int]) -> list[int]:
    \"\"\"Return only even numbers from the list.\"\"\"
```

Return only the completed function.""",
        expected_contains=["return", "%", "2", "for", "if"],
        context_size=40,
        ideal_max_tokens=100,
    ),

    # === CODE FIX ===
    BenchmarkTask(
        name="fix_off_by_one",
        category="code_fix",
        prompt="""Fix the bug in this code:

```python
def get_last_element(arr):
    return arr[len(arr)]  # IndexError!
```

Explain the bug and provide the fix.""",
        expected_contains=["len(arr) - 1", "-1"],
        context_size=40,
        ideal_max_tokens=150,
    ),

    BenchmarkTask(
        name="fix_null_check",
        category="code_fix",
        prompt="""Fix the NullPointerException in this Kotlin code:

```kotlin
fun getUserName(user: User?): String {
    return user.name.uppercase()
}
```

Provide the corrected code.""",
        expected_contains=["?.", "?:", "null", "let"],
        context_size=50,
        ideal_max_tokens=150,
    ),

    # === EXPLANATION ===
    BenchmarkTask(
        name="explain_recursion",
        category="explanation",
        prompt="""Explain what this code does in 2-3 sentences:

```python
def mystery(n):
    if n <= 1:
        return n
    return mystery(n-1) + mystery(n-2)
```""",
        expected_contains=["fibonacci", "recursive"],
        context_size=50,
        ideal_max_tokens=100,
    ),

    # === REASONING / MATH ===
    BenchmarkTask(
        name="math_word_problem",
        category="reasoning",
        prompt="""A store sells apples for $2 each. If you buy 5 or more, you get 20% off the total.
How much do 7 apples cost? Show your calculation.""",
        expected_contains=["14", "11.2", "0.8", "20%"],
        context_size=50,
        ideal_max_tokens=150,
    ),

    BenchmarkTask(
        name="logic_puzzle",
        category="reasoning",
        prompt="""If all Bloops are Razzies, and all Razzies are Lazzies, are all Bloops Lazzies?
Answer yes or no and explain why in one sentence.""",
        expected_contains=["yes", "Yes", "YES"],
        context_size=40,
        ideal_max_tokens=80,
    ),

    # === JSON EXTRACTION (tests low temperature) ===
    BenchmarkTask(
        name="json_extraction",
        category="structured",
        prompt="""Extract the following into valid JSON with keys "name", "age", "city":

"John Smith is 32 years old and lives in Tokyo."

Return ONLY the JSON object, no explanation.""",
        expected_contains=['"name"', '"age"', '"city"', "John", "32", "Tokyo"],
        context_size=50,
        ideal_max_tokens=80,
    ),

    # === CONTEXT HANDLING (longer prompt) ===
    BenchmarkTask(
        name="summarize_code",
        category="context",
        prompt="""Summarize what this code does in one sentence:

```kotlin
class ConversationRepository(
    private val localDataSource: ConversationLocalDataSource,
    private val llmService: LLMService
) {
    suspend fun getConversations(): List<ConversationInfo> {
        return localDataSource.getConversations()
    }

    suspend fun getConversation(id: Long): Conversation? {
        return localDataSource.getConversation(id)
    }

    suspend fun createConversation(name: String): Long {
        return localDataSource.createConversation(name)
    }

    suspend fun deleteConversation(id: Long) {
        localDataSource.deleteConversation(id)
    }

    suspend fun sendMessage(
        conversationId: Long,
        message: String,
        model: LLMModel
    ): Flow<String> {
        val conversation = getConversation(conversationId)
            ?: throw IllegalStateException("Conversation not found")
        return llmService.chat(conversation.messages + UserMessage(message), model)
    }
}
```""",
        expected_contains=["repository", "conversation", "database", "message"],
        context_size=250,
        ideal_max_tokens=100,
    ),
]


@dataclass
class BenchmarkResult:
    task_name: str
    category: str
    model: str
    temperature: float
    max_tokens: int
    num_ctx: int
    quantization: str
    response: str
    time_seconds: float
    tokens_generated: int
    tokens_per_second: float
    expected_found: int
    expected_total: int
    success_rate: float


def call_ollama(
    model: str,
    prompt: str,
    temperature: float = 0.7,
    max_tokens: int = 256,
    num_ctx: int = 2048,
    base_url: str = OLLAMA_BASE_URL,
) -> tuple[str, float, int]:
    """
    Call Ollama API and return (response, time_seconds, tokens_generated).
    """
    url = f"{base_url}/api/generate"

    payload = {
        "model": model,
        "prompt": prompt,
        "stream": False,
        "options": {
            "temperature": temperature,
            "num_predict": max_tokens,
            "num_ctx": num_ctx,
        }
    }

    start_time = time.perf_counter()

    try:
        response = requests.post(url, json=payload, timeout=120)
        response.raise_for_status()
        result = response.json()

        elapsed = time.perf_counter() - start_time
        text = result.get("response", "")

        # Get token count from eval_count if available
        tokens = result.get("eval_count", len(text.split()))

        return text, elapsed, tokens

    except requests.exceptions.RequestException as e:
        print(f"  ERROR: {e}")
        return f"ERROR: {e}", 0.0, 0


def evaluate_response(response: str, expected_contains: list[str]) -> tuple[int, int]:
    """Check how many expected strings are in the response."""
    found = sum(1 for exp in expected_contains if exp.lower() in response.lower())
    return found, len(expected_contains)


def run_benchmark(
    model: str,
    temperatures: list[float],
    max_tokens_list: list[int],
    num_ctx_list: list[int],
    quantization: str = "unknown",
    tasks: Optional[list[BenchmarkTask]] = None,
    base_url: str = OLLAMA_BASE_URL,
) -> list[BenchmarkResult]:
    """Run benchmark with all parameter combinations."""

    if tasks is None:
        tasks = TASKS

    results = []
    total_runs = len(tasks) * len(temperatures) * len(max_tokens_list) * len(num_ctx_list)
    current_run = 0

    print(f"\n{'='*60}")
    print(f"BENCHMARK: {model} ({quantization})")
    print(f"Tasks: {len(tasks)}, Temperatures: {temperatures}")
    print(f"Max tokens: {max_tokens_list}, Context sizes: {num_ctx_list}")
    print(f"Total runs: {total_runs}")
    print(f"{'='*60}\n")

    for task in tasks:
        for temp in temperatures:
            for max_tok in max_tokens_list:
                for num_ctx in num_ctx_list:
                    current_run += 1
                    print(f"[{current_run}/{total_runs}] {task.name} | temp={temp}, max_tok={max_tok}, ctx={num_ctx}")

                    response, elapsed, tokens = call_ollama(
                        model=model,
                        prompt=task.prompt,
                        temperature=temp,
                        max_tokens=max_tok,
                        num_ctx=num_ctx,
                        base_url=base_url,
                    )

                    found, total = evaluate_response(response, task.expected_contains)
                    tps = tokens / elapsed if elapsed > 0 else 0

                    result = BenchmarkResult(
                        task_name=task.name,
                        category=task.category,
                        model=model,
                        temperature=temp,
                        max_tokens=max_tok,
                        num_ctx=num_ctx,
                        quantization=quantization,
                        response=response[:500],  # Truncate for storage
                        time_seconds=round(elapsed, 2),
                        tokens_generated=tokens,
                        tokens_per_second=round(tps, 1),
                        expected_found=found,
                        expected_total=total,
                        success_rate=round(found / total * 100, 1) if total > 0 else 0,
                    )
                    results.append(result)

                    print(f"  -> {elapsed:.2f}s, {tokens} tokens ({tps:.1f} t/s), score: {found}/{total}")

    return results


def save_results(results: list[BenchmarkResult], output_dir: Path):
    """Save results to CSV and JSON."""
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    # CSV
    csv_path = output_dir / f"benchmark_{timestamp}.csv"
    with open(csv_path, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([
            "task", "category", "model", "quantization", "temperature",
            "max_tokens", "num_ctx", "time_s", "tokens", "tokens_per_s",
            "score", "score_pct", "response_preview"
        ])
        for r in results:
            writer.writerow([
                r.task_name, r.category, r.model, r.quantization, r.temperature,
                r.max_tokens, r.num_ctx, r.time_seconds, r.tokens_generated,
                r.tokens_per_second, f"{r.expected_found}/{r.expected_total}",
                r.success_rate, r.response[:100].replace("\n", " ")
            ])

    # JSON (full responses)
    json_path = output_dir / f"benchmark_{timestamp}.json"
    with open(json_path, "w") as f:
        json.dump([vars(r) for r in results], f, indent=2)

    print(f"\nResults saved to:\n  {csv_path}\n  {json_path}")
    return csv_path, json_path


def print_summary(results: list[BenchmarkResult]):
    """Print a summary of benchmark results."""
    print(f"\n{'='*60}")
    print("SUMMARY")
    print(f"{'='*60}")

    # Group by category
    categories = {}
    for r in results:
        if r.category not in categories:
            categories[r.category] = []
        categories[r.category].append(r)

    for cat, cat_results in categories.items():
        avg_score = sum(r.success_rate for r in cat_results) / len(cat_results)
        avg_tps = sum(r.tokens_per_second for r in cat_results) / len(cat_results)
        print(f"\n{cat.upper()}")
        print(f"  Avg Score: {avg_score:.1f}%")
        print(f"  Avg Speed: {avg_tps:.1f} tokens/s")

    # Best parameters per category
    print(f"\n{'='*60}")
    print("BEST PARAMETERS BY CATEGORY")
    print(f"{'='*60}")

    for cat, cat_results in categories.items():
        best = max(cat_results, key=lambda r: (r.success_rate, r.tokens_per_second))
        print(f"\n{cat}: temp={best.temperature}, max_tok={best.max_tokens}, ctx={best.num_ctx}")
        print(f"  Score: {best.success_rate}%, Speed: {best.tokens_per_second} t/s")

    # Temperature analysis
    print(f"\n{'='*60}")
    print("TEMPERATURE ANALYSIS")
    print(f"{'='*60}")

    temps = sorted(set(r.temperature for r in results))
    for temp in temps:
        temp_results = [r for r in results if r.temperature == temp]
        avg_score = sum(r.success_rate for r in temp_results) / len(temp_results)
        print(f"  temp={temp}: avg_score={avg_score:.1f}%")


def list_models(base_url: str = OLLAMA_BASE_URL) -> list[str]:
    """List available Ollama models."""
    try:
        response = requests.get(f"{base_url}/api/tags")
        response.raise_for_status()
        models = response.json().get("models", [])
        return [m["name"] for m in models]
    except Exception as e:
        print(f"Error listing models: {e}")
        return []


def main():
    parser = argparse.ArgumentParser(description="Benchmark local LLM parameters")
    parser.add_argument("--model", "-m", default="llama3.2:1b", help="Model name (default: llama3.2:1b)")
    parser.add_argument("--quantization", "-q", default="Q4_K_M", help="Quantization label for logging")
    parser.add_argument("--temperatures", "-t", nargs="+", type=float, default=[0.0, 0.3, 0.7, 1.0],
                        help="Temperature values to test")
    parser.add_argument("--max-tokens", nargs="+", type=int, default=[128, 256],
                        help="Max token values to test")
    parser.add_argument("--contexts", "-c", nargs="+", type=int, default=[1024, 2048],
                        help="Context window sizes to test")
    parser.add_argument("--base-url", default=OLLAMA_BASE_URL, help="Ollama API base URL")
    parser.add_argument("--output", "-o", default="./results", help="Output directory")
    parser.add_argument("--list-models", action="store_true", help="List available models and exit")
    parser.add_argument("--quick", action="store_true", help="Quick mode: fewer parameter combinations")
    parser.add_argument("--tasks", nargs="+", help="Run specific tasks by name")

    args = parser.parse_args()

    if args.list_models:
        print("Available models:")
        for m in list_models(args.base_url):
            print(f"  - {m}")
        return

    # Quick mode uses fewer parameters
    if args.quick:
        args.temperatures = [0.0, 0.7]
        args.max_tokens = [256]
        args.contexts = [2048]

    # Filter tasks if specified
    tasks = TASKS
    if args.tasks:
        tasks = [t for t in TASKS if t.name in args.tasks]
        if not tasks:
            print(f"No matching tasks found. Available: {[t.name for t in TASKS]}")
            return

    print(f"Testing model: {args.model}")
    print(f"Available models: {list_models(args.base_url)}")

    results = run_benchmark(
        model=args.model,
        temperatures=args.temperatures,
        max_tokens_list=args.max_tokens,
        num_ctx_list=args.contexts,
        quantization=args.quantization,
        tasks=tasks,
        base_url=args.base_url,
    )

    save_results(results, Path(args.output))
    print_summary(results)


if __name__ == "__main__":
    main()
