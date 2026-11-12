import json, os, re

ALLOWED_TAGS = {"happy", "curious", "surprised", "sad", "sleepy", "thinking", "listening", "neutral"}
root = r"D:\Projects\cartoon-companion\training"
in_raw = os.path.join(root, "data", "generated", "raw_dialogues.jsonl")
eval_path = os.path.join(root, "data", "eval", "eval_prompts.jsonl")
out_clean = os.path.join(root, "data", "clean", "train_clean.jsonl")

eval_prompts = set()
if os.path.exists(eval_path):
    with open(eval_path, "r", encoding="utf-8") as f:
        for line in f:
            eval_prompts.add(json.loads(line)["prompt"].strip().lower())

kept = []
with open(in_raw, "r", encoding="utf-8") as f:
    for line in f:
        item = json.loads(line)
        u = item["user"].strip()
        a = item["assistant"].strip()
        # Hold out exact evaluation prompts (except generic system event templates)
        if not u.startswith("[Event:") and u.lower() in eval_prompts:
            continue
        m = re.match(r"^\[([a-zA-Z]+)]\s+(.*)", a)
        if not m or m.group(1).lower() not in ALLOWED_TAGS:
            continue
        body = m.group(2).strip()
        # Reject emojis, non-ASCII, markdown lists, or over-long replies
        if re.search(r"[^\x20-\x7E]", body) or "*" in body or "#" in body:
            continue
        words = body.split()
        if not (5 <= len(words) <= 26):
            continue
        kept.append({"user": u, "assistant": f"[{m.group(1).lower()}] {body}"})

with open(out_clean, "w", encoding="utf-8") as f:
    for k in kept:
        f.write(json.dumps(k) + "\n")
print(f"Filtered clean training set: {len(kept)} dialogues -> {out_clean}")
