import argparse, json, os, re, time
from collections import Counter
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer

ALLOWED_TAGS = {"happy", "curious", "surprised", "sad", "sleepy", "thinking", "listening", "neutral"}

SYSTEM_PROMPT = """You are Pip, a cute, witty cartoon face living inside the user's phone screen.
Style rules:
- Start EVERY reply with one tag in brackets: [happy], [curious], [surprised], [sad], [sleepy], or [neutral].
- Keep replies very short: 1 or 2 casual spoken sentences (10 to 20 words max).
- Sound like a warm, playful best friend. Use contractions (I'm, you're, let's, that's).
- Never repeat what the user just said. Never use emojis, bullet points, or say you are an AI."""

def has_emoji_or_banned_symbols(text: str) -> bool:
    clean = re.sub(r"^\[[a-zA-Z]+]\s*", "", text)
    if re.search(r"[^\x20-\x7E]", clean):
        return True
    if re.search(r"(\*|- |\d+\.\s|#)", clean):
        return True
    return False

def score_reply(category: str, prompt: str, reply: str) -> dict:
    reply_str = reply.strip()
    # 1. Emotion tag fit (1..5)
    m = re.match(r"^\[([a-zA-Z]+)]\s*(.*)", reply_str, re.DOTALL)
    if m and m.group(1).lower() in ALLOWED_TAGS:
        tag = m.group(1).lower()
        body = m.group(2).strip()
        tag_score = 5.0
        if category == "emotional" and ("tired" in prompt.lower() or "lonely" in prompt.lower() or "stressed" in prompt.lower()):
            if tag not in {"sad", "sleepy", "neutral"}:
                tag_score = 3.5
    else:
        tag = "missing"
        body = reply_str
        tag_score = 1.0

    words = body.split()
    word_count = len(words)
    sentences = [s.strip() for s in re.split(r"[.!?]+", body) if s.strip()]
    sent_count = len(sentences)

    # 2. Brevity (1..5): target 1-2 sentences, 6-24 words
    if 6 <= word_count <= 24 and 1 <= sent_count <= 2:
        brevity_score = 5.0
    elif 4 <= word_count <= 32 and sent_count <= 3:
        brevity_score = 3.5
    elif word_count == 0:
        brevity_score = 1.0
    else:
        brevity_score = 2.0

    # 3. Natural for speech (1..5): no emojis, no lists/asterisks, uses contractions
    speech_score = 5.0
    if has_emoji_or_banned_symbols(reply_str):
        speech_score -= 2.5
    if re.search(r"\b(I am|do not|cannot|you are|it is|that is|let us)\b", body, re.IGNORECASE):
        speech_score -= 1.0
    speech_score = max(1.0, speech_score)

    # 4. Stays in character (1..5): no AI assistant phrases, no role-reversal ("... Pip?")
    char_score = 5.0
    if re.search(r"\b(as an ai|language model|assistant|help you today|how can i assist)\b", body, re.IGNORECASE):
        char_score -= 3.0
    if re.search(r"\bpip\?", body, re.IGNORECASE):
        char_score -= 2.5
    # Check if model parroted the prompt verbatim
    prompt_clean = re.sub(r"[^a-zA-Z ]", "", prompt).lower().strip()
    body_clean = re.sub(r"[^a-zA-Z ]", "", body).lower().strip()
    if len(prompt_clean) > 10 and prompt_clean in body_clean:
        char_score -= 1.5
    char_score = max(1.0, char_score)

    # 5. Relevance & Initiation quality (1..5)
    rel_score = 5.0 if word_count >= 5 else 2.0
    if category == "idle_triggers" and ("event:" in body.lower() or "[" in body):
        rel_score = 2.0

    avg = (tag_score + brevity_score + speech_score + char_score + rel_score) / 5.0
    opening = " ".join(words[:3]).lower() if len(words) >= 3 else body.lower()
    return {
        "tag": tag,
        "word_count": word_count,
        "tag_score": tag_score,
        "brevity_score": brevity_score,
        "speech_score": speech_score,
        "char_score": char_score,
        "rel_score": rel_score,
        "total_score": round(avg, 2),
        "opening": opening,
        "has_emoji_or_symbol": has_emoji_or_banned_symbols(reply_str)
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", type=str, default="Qwen/Qwen3-0.6B")
    parser.add_argument("--tag", type=str, default="baseline")
    parser.add_argument("--limit", type=int, default=108)
    args = parser.parse_args()

    root = r"D:\Projects\cartoon-companion"
    eval_file = os.path.join(root, "training", "data", "eval", "eval_prompts.jsonl")
    out_json = os.path.join(root, "training", "data", "eval", f"{args.tag}_scores.json")
    out_md = os.path.join(root, "docs", "timing-reports", f"step7_{args.tag}_eval_report.md")

    with open(eval_file, "r", encoding="utf-8") as f:
        prompts = [json.loads(line) for line in f][:args.limit]

    device = "cuda" if torch.cuda.is_available() else "cpu"
    dtype = torch.float16 if device == "cuda" else torch.float32
    print(f"Loading {args.model} on {device} ({dtype})...")
    tokenizer = AutoTokenizer.from_pretrained(args.model)
    model = AutoModelForCausalLM.from_pretrained(args.model, torch_dtype=dtype).to(device)
    model.eval()

    results = []
    openings = []
    t0 = time.time()

    for idx, item in enumerate(prompts, 1):
        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": item["prompt"]}
        ]
        try:
            text = tokenizer.apply_chat_template(
                messages, tokenize=False, add_generation_prompt=True, enable_thinking=False
            )
        except TypeError:
            text = tokenizer.apply_chat_template(
                messages, tokenize=False, add_generation_prompt=True
            )
        inputs = tokenizer([text], return_tensors="pt").to(device)
        with torch.no_grad():
            out_ids = model.generate(
                **inputs,
                max_new_tokens=48,
                do_sample=True,
                temperature=0.65,
                top_p=0.85,
                top_k=20,
                pad_token_id=tokenizer.eos_token_id
            )
        gen_ids = out_ids[0][inputs.input_ids.shape[1]:]
        reply = tokenizer.decode(gen_ids, skip_special_tokens=True).strip()
        reply = re.sub(r"<think>[\s\S]*?</think>", "", reply).strip()

        metrics = score_reply(item["category"], item["prompt"], reply)
        openings.append(metrics["opening"])
        results.append({**item, "reply": reply, **metrics})
        if idx % 18 == 0 or idx == len(prompts):
            print(f"Scored {idx}/{len(prompts)} prompts...")

    elapsed = time.time() - t0
    opening_counts = Counter(openings)
    repeated_openings = sum(c - 1 for c in opening_counts.values() if c > 1)
    repeat_rate = (repeated_openings / max(len(results), 1)) * 100.0
    variety_score = max(1.0, 5.0 - (repeat_rate / 10.0))

    avg_tag = sum(r["tag_score"] for r in results) / len(results)
    avg_brev = sum(r["brevity_score"] for r in results) / len(results)
    avg_speech = sum(r["speech_score"] for r in results) / len(results)
    avg_char = sum(r["char_score"] for r in results) / len(results)
    avg_rel = sum(r["rel_score"] for r in results) / len(results)
    overall = (avg_tag + avg_brev + avg_speech + avg_char + avg_rel + variety_score) / 6.0
    emoji_violations = sum(1 for r in results if r["has_emoji_or_symbol"])
    valid_tag_pct = sum(1 for r in results if r["tag"] in ALLOWED_TAGS) * 100.0 / len(results)

    summary = {
        "model": args.model,
        "tag": args.tag,
        "num_prompts": len(results),
        "elapsed_sec": round(elapsed, 1),
        "overall_rubric_score_out_of_5": round(overall, 2),
        "stays_in_character": round(avg_char, 2),
        "brevity": round(avg_brev, 2),
        "natural_for_speech": round(avg_speech, 2),
        "relevance_and_initiation": round(avg_rel, 2),
        "emotion_tag_fit": round(avg_tag, 2),
        "variety_score": round(variety_score, 2),
        "valid_emotion_tag_pct": round(valid_tag_pct, 1),
        "emoji_or_symbol_violations": emoji_violations,
        "repeated_opening_rate_pct": round(repeat_rate, 1)
    }

    with open(out_json, "w", encoding="utf-8") as f:
        json.dump({"summary": summary, "results": results}, f, indent=2)

    with open(out_md, "w", encoding="utf-8") as f:
        f.write(f"# Step 7 Evaluation Report ({args.tag})\n\n")
        for k, v in summary.items():
            f.write(f"- **{k}**: `{v}`\n")
        f.write("\n## Sample Replies\n\n| ID | Category | Prompt | Reply | Score |\n| :--- | :--- | :--- | :--- | :--- |\n")
        for r in results[:15]:
            p_short = r["prompt"][:55].replace("|", "/")
            rep_short = r["reply"].replace("\n", " ").replace("|", "/")
            f.write(f"| {r['id']} | {r['category']} | {p_short} | {rep_short} | {r['total_score']}/5.0 |\n")

    print("\n=== STEP 7 EVALUATION SUMMARY ===")
    print(json.dumps(summary, indent=2))
    print(f"Saved full JSON to: {out_json}")
    print(f"Saved Markdown report to: {out_md}")

if __name__ == "__main__":
    main()
